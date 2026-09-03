# Phase 0 Research: Provider Failure Isolation

## 1. Governed Stack and Dependency Resolution

**Decision**: Use Java 26 without preview features, Spring Boot 4.1.x, Maven, and Resilience4j 2.4.0. Declare `resilience4j-spring-boot4` version 2.4.0 explicitly instead of relying only on the Resilience4j BOM. Make dependency resolution, application-context startup, provider registry creation, Actuator exposure, Micrometer binding, and a Java 26 runtime smoke test the first implementation gate.

**Rationale**: Constitution 2.0.0 mandates Java 26 and Spring Boot 4.1.x, superseding older Boot 3 references. Spring Boot 4.1.1 officially supports Java 26. Resilience4j 2.4.0 adds Spring Boot 4 support, but the 2.4.0 BOM omitted the Boot 4 starter even though the artifact is published; an explicit starter version removes that ambiguity.

**Alternatives considered**:

- Spring Boot 3.5.x conflicts with the current constitution.
- Depending only on the Resilience4j 2.4.0 BOM leaves the Boot 4 starter unmanaged.
- Using unreleased Resilience4j snapshots adds avoidable supply-chain and reproducibility risk.

**Sources**: [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Resilience4j 2.4.0 release](https://github.com/resilience4j/resilience4j/releases), [Resilience4j BOM issue](https://github.com/resilience4j/resilience4j/issues/2427), [published Boot 4 starter](https://javadoc.io/doc/io.github.resilience4j/resilience4j-spring-boot4/2.4.0)

## 2. Application and Provider Boundaries

**Decision**: Retain the planned two-module Maven reactor: `delivery-orchestrator` and `provider-simulator`. Keep all resilience behavior in the orchestrator. Every provider adapter owns its HTTP DTOs, trusted provider identity, `RestClient`, timeouts, bulkhead, retry policy, circuit instance, fallback mapping, and telemetry identity. Do not add a shared Java contract or domain module.

**Rationale**: The simulator stands in for replaceable external providers, so compile-time DTO sharing would weaken the adapter boundary. Provider-owned policy objects make isolation testable and prevent a single provider from consuming another provider's resilience budget.

**Alternatives considered**:

- A universal switch-based provider client obscures independent configuration and state.
- A shared provider-contract module couples the orchestrator to the simulator implementation.
- Separate repositories add delivery overhead without improving the current three-provider MVP.

## 3. Canonical Failure Classification

**Decision**: Centralize adapter responses and exceptions in one provider failure classifier used consistently by retry, circuit accounting, fallback, logs, metrics, and tests.

| Source outcome | Retry | Circuit accounting | Terminal classification |
|---|---:|---:|---|
| Valid, well-formed quote | No | Success | `VALID_QUOTE` |
| Valid no-driver response | No | Ignored | `BUSINESS_UNAVAILABLE` |
| Connection refused/reset or connect/response timeout | If budget permits | Failure after the logical call exhausts | `RETRYABLE_TECHNICAL_FAILURE` |
| `408`, `429`, approved `500`, `502`, `503`, or `504` | If policy and budget permit | Failure after the logical call exhausts | `RETRYABLE_TECHNICAL_FAILURE` |
| Other deterministic `4xx` or provider validation rejection | No | Ignored | `NON_RETRYABLE_TECHNICAL_FAILURE` |
| Permanent TLS, hostname, URL, or local configuration failure | No | Ignored and operationally alerted | `NON_RETRYABLE_TECHNICAL_FAILURE` |
| Invalid or malformed provider quote/response | No | Failure | `NON_RETRYABLE_TECHNICAL_FAILURE` |
| Open or excess half-open rejection | No | Already represented by breaker rejection | `CIRCUIT_EXCLUDED` |
| Provider bulkhead or executor rejection | No | Ignored | `LOCAL_CAPACITY_REJECTION` |
| Request deadline exhausted before provider contact | No | Ignored | `DEADLINE_EXHAUSTED` |
| Cancelled orchestration | No | Ignored | `CANCELLED` |

Unknown `5xx` values are non-retryable unless explicitly allowlisted. Fatal JVM `Error` values are never converted into normal provider outcomes.

**Rationale**: One classifier prevents contradictory policy decisions. Business unavailability and local saturation must not make the provider look technically unhealthy, while an invalid provider payload is a provider contract failure that should eventually isolate a persistently broken provider.

**Alternatives considered**:

- Retrying every I/O exception would retry permanent TLS and configuration problems.
- Retrying every `5xx` includes deterministic responses such as `501` and `505`.
- Adapter-local classifiers would drift as providers are added.

## 4. Explicit Resilience Composition

**Decision**: Use functional composition and explicit circuit permission/accounting rather than stacked resilience annotations. The effective order is:

```text
Immutable request deadline and concurrent aggregation
  └─ provider-neutral fallback
      └─ circuit permission and one terminal logical-call accounting event
          └─ logical provider TimeLimiter
              └─ provider-specific ThreadPoolBulkhead
                  └─ deadline-aware Retry (maximum three total attempts)
                      └─ explicit transport connection/response timeouts
                          └─ RestClient provider call
```

The circuit gate runs before provider bulkhead admission, so an open provider fails without consuming a queue slot. The bulkhead contains the entire retry lifecycle, so retries and backoff cannot escape that provider's concurrency bound. The logical time limit includes queue time, attempts, and backoffs; transport timeouts remain authoritative for terminating blocking network work. Fallback runs last and cannot hide the original condition before retry or circuit policy observes it.

**Rationale**: Functional composition makes the constitution-required order visible and directly testable. Circuit outside retry records one terminal logical outcome per restaurant request/provider participation rather than allowing one three-attempt request to consume three circuit samples.

**Alternatives considered**:

- Retry outside circuit can inflate the circuit failure rate with every attempt.
- Circuit inside the bulkhead makes open-circuit requests consume local capacity.
- Annotation fallback ordering is indirect and makes negative-path tests less conclusive.
- A TimeLimiter without transport timeouts may return early while blocking network work continues.

**Sources**: [Resilience4j functional patterns and Spring integration](https://resilience4j.readme.io/docs/getting-started-3), [Resilience4j circuit breaker behavior](https://resilience4j.readme.io/docs/circuitbreaker), [Spring REST clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)

## 5. Deadlines, Timeouts, and Late Results

**Decision**: Establish an immutable deadline from an injectable monotonic ticker when the request enters the orchestrator. Snapshot the active providers, policies, and ranking configuration at the same point. Reserve positive time for ranking and response mapping. Before provider admission, every retry delay, and every queued task start, check whether the remaining time can still fit the configured delay, full next-attempt budget, and finalization reserve. Configure `cancelRunningFuture=true`, but treat cancellation as best effort and retain explicit connection/response timeouts. Freeze the aggregation result once; outcomes completed after the deadline are diagnostic-only.

```text
retry allowed only when
remaining time >= retry delay + configured attempt budget + finalization reserve
```

**Rationale**: A monotonic deadline is immune to wall-clock adjustments. Rechecking after queueing prevents a task cancelled at the request boundary from later contacting a provider. One finalization point guarantees late responses cannot modify the returned ranking or turn a `503` into a success after the fact.

**Alternatives considered**:

- Independent relative timeouts can cumulatively exceed the public response deadline.
- `CompletableFuture.orTimeout` alone does not reliably stop blocking HTTP work.
- Sequential future joins can spend the deadline repeatedly.

**Source**: [Resilience4j TimeLimiter](https://resilience4j.readme.io/docs/timeout)

## 6. Retry Backoff, Jitter, and `Retry-After`

**Decision**: Count the initial call as attempt one and allow at most three total attempts. Use validated exponential backoff with non-negative bounded jitter that preserves increasing attempt windows. Inject the random source for deterministic tests. For retryable `429` and `503`, parse `Retry-After` as delta-seconds or an HTTP date, choose the greater of local backoff and a valid server-requested delay, cap it at the configured maximum, and skip the retry if the resulting delay plus a full attempt cannot fit. Preserve the same correlation identifier across all attempts.

**Rationale**: Resilience4j defines `maxAttempts` as including the initial call and supports exception predicates and outcome-aware interval functions. Honoring a longer provider delay avoids retrying before the provider asks, while the request deadline prevents that advice from delaying healthy-provider results.

**Alternatives considered**:

- Fixed delays violate the constitution's exponential-backoff requirement.
- Unbounded full jitter can produce decreasing successive delays.
- Blindly honoring `Retry-After` beyond the deadline defeats partial success.

**Sources**: [Resilience4j Retry](https://resilience4j.readme.io/docs/retry), [RFC 6585 rate limiting](https://www.rfc-editor.org/rfc/rfc6585), [RFC 9110 `Retry-After`](https://www.rfc-editor.org/rfc/rfc9110)

## 7. Circuit Accounting and Recovery

**Decision**: Maintain one stable in-memory `CircuitBreaker` per provider. Use externally validated count-window size, minimum calls, failure threshold, open interval, half-open permit count, and finite half-open maximum duration. Keep automatic open-to-half-open monitoring disabled; the first real request after the open interval triggers half-open evaluation. Acquire one permission per logical provider participation and record at most one terminal result:

- Valid quote → success.
- Exhausted retryable failure, provider timeout after contact, or invalid provider payload → failure.
- Business unavailable, deterministic `4xx`, local capacity rejection, or deadline exhaustion before contact → ignored for provider health.
- Cancellation before actual use → release permission.
- Open or excess half-open request → no outbound call and no additional health sample.

Half-open probes use exactly one attempt each; they are not retried. Start with two probe permits as a conservative configurable MVP default.

**Rationale**: One logical sample prevents a single request's retries from opening a provider circuit. Single-attempt probes provide unmasked recovery evidence, and Resilience4j already bounds concurrent half-open permits.

**Alternatives considered**:

- Counting every attempt accelerates opening based on client policy rather than independent requests.
- Retrying recovery probes hides continued provider instability.
- Recording no-drivers as success dilutes technical failure-rate calculations.
- Persistent circuit state is explicitly out of scope.

## 8. Bounded Concurrency and Aggregation

**Decision**: Give each provider a distinct fixed/max thread-pool bulkhead, finite queue, stable low-cardinality name, and shutdown lifecycle. Hold one provider slot across its entire retry/backoff lifecycle. Bulkhead rejection immediately maps to an unavailable outcome, is not retried, and does not affect circuit health. Begin all provider pipelines before waiting; make every future complete normally with a provider-neutral outcome, then perform one bounded combined wait until the overall deadline.

At finalization:

1. Freeze exactly once.
2. Include only valid quotes completed no later than the deadline.
3. List each provider without a valid quote exactly once in canonical provider-ID order.
4. Rank valid quotes with the existing policy.
5. Set `partialResult` when any snapshotted provider is unavailable.
6. Return a controlled `503` when there are zero valid quotes.
7. Cancel pending work best effort without allowing late mutation.

**Rationale**: Provider-local admission prevents retry storms and cross-provider starvation. Normalizing failures before aggregation avoids all-or-nothing exceptional completion, while a single bounded wait prevents timeout multiplication.

**Alternatives considered**:

- One shared provider pool allows one failing provider to consume healthy capacity.
- A bulkhead per retry attempt allows many logical calls to wait outside provider admission.
- Cached quote fallback is out of scope.
- Concurrent mutation of a response object creates finalization races.

**Source**: [Resilience4j Bulkhead](https://resilience4j.readme.io/docs/bulkhead)

## 9. Public and Internal Contracts

**Decision**: Preserve the feature-001 public request and complete-success body. Generalize the `200` result so `unavailableProviders` can contain unique provider IDs and `partialResult` can be true. Keep detailed unavailable reasons internal because changing the existing provider-ID array to objects would break clients. Add `503 application/problem+json` with a stable RFC 9457 type, correlation ID, safe detail, and unavailable provider IDs, but no status histories, exception text, hostnames, or circuit internals.

For the internal provider operation, define a discriminated `200` result with `QUOTE_AVAILABLE` and `NO_DRIVERS`, a deterministic non-retryable `400`, a `429` with optional `Retry-After`, and a technical `503`. Provider identity remains trusted adapter configuration rather than provider response data.

**Rationale**: Complete results remain compatible, partial results become explicit, and the total-failure contract is actionable without leaking infrastructure details. A valid no-driver body prevents business unavailability from being misclassified as an HTTP failure.

**Alternatives considered**:

- Changing `unavailableProviders` to reason objects is a breaking public change.
- Representing no-drivers as `4xx` or `5xx` invites incorrect retry and circuit behavior.
- A bespoke total-failure envelope loses standard problem semantics.

**Sources**: [OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0), [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html)

## 10. Correlation and Observability

**Decision**: Pass an immutable request context containing correlation ID, monotonic deadline, and configuration snapshots explicitly through orchestration. Keep correlation separate from W3C trace identifiers. Build clients from Spring Boot's configured `RestClient.Builder`, propagate the correlation header on every attempt, and capture/restore observation and logging context across provider and scheduler threads.

Combine Resilience4j meters with business meters for complete/partial/all-unavailable requests, useful results, provider logical outcomes, attempts, retry decisions, timeouts, circuit state/rejections, bulkhead saturation, fallbacks, and finalization. Tags are bounded to provider and stable classifications. Structured events include correlation ID, provider, attempt, safe outcome, duration, selected delay, circuit state, and usefulness; they exclude bodies, coordinates, order value, secrets, raw exception messages, and business/correlation identifiers as metric tags. One open provider does not make orchestrator readiness fail.

**Rationale**: Explicit context remains correct even if thread-local propagation is misconfigured. Business meters answer whether the operation remained useful, which library meters alone cannot. Treating one provider as a degraded dependency preserves the constitution's partial-success model.

**Alternatives considered**:

- MDC-only propagation can lose correlation across custom executors.
- Using a trace ID as the public correlation ID cannot preserve caller values exactly.
- Provider health indicators controlling readiness would disable healthy providers indirectly.

**Sources**: [Spring Boot observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html), [Micrometer context propagation](https://docs.micrometer.io/tracing/reference/configuring.html), [Resilience4j metrics](https://resilience4j.readme.io/docs/getting-started-3)

## 11. Verification and Dependency Sequencing

**Decision**: Use deterministic layered evidence: classifier and predicate units; fake ticker/random retry tests; explicit operator-order and single-circuit-sample tests; circuit transition/probe tests without multi-second sleeps; MockMvc/OpenAPI public contracts; one WireMock server per provider for real transport behavior; latch/barrier saturation and finalization races; safe log/metric assertions; Compose scenarios; and 20 warm-ups plus 200 measured requests at concurrency 10 over a deterministic failure matrix.

Feature 001 must supply the Maven skeleton, successful ranking flow, and base contracts after reconciliation to Boot 4.1.x. Feature 002 remains independently testable with WireMock while feature 003's simulator plan is rerun and implemented. Compose-backed scenario acceptance is sequenced only after feature 003 exists.

**Rationale**: Virtual time and injected randomness keep policy tests fast and repeatable; real mock HTTP boundaries prove socket/status behavior; request-count assertions prove negative cases such as no retry and open-circuit rejection. Explicit sequencing acknowledges that the repository currently contains design artifacts rather than runnable applications.

**Alternatives considered**:

- Mockito-only tests cannot prove connection/response timeouts or request counts at the HTTP boundary.
- Real sleeps make retry and circuit tests slow and flaky.
- Compose-only tests make classifier and ordering failures difficult to isolate.
- Planning as if dependencies already exist would make the quickstart misleading.

**Source**: [Spring Framework client test guidance](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html)
