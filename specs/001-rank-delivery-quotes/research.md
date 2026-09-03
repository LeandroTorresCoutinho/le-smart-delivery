# Phase 0 Research: Ranked Delivery Quotes

## 1. Governed Java and Spring Boot Pairing

**Decision**: Use Java 26 without preview features and pin Spring Boot 3.5.16, as required by the constitution. Make a minimal two-application compatibility proof the first implementation gate: compile, test, start both applications on Linux/JDK 26, and verify application context creation, JSON serialization, validation, Actuator health, `RestClient`, Micrometer, and Resilience4j configuration/registry creation in local and CI environments.

**Rationale**: Spring Boot 3.5.16 officially supports Java only through 25, while current Spring Boot 4 supports Java 26. The constitution nonetheless mandates Java 26 and Spring Boot 3, and governance forbids silently changing either. A compatibility proof establishes empirical operability while making clear that it does not create official vendor support.

**Alternatives considered**:

- Java 25 with Spring Boot 3.5.16 is officially supported but requires a constitution amendment.
- Java 26 with Spring Boot 4.1.x is officially supported but requires a constitution amendment.
- Compiling with `--release 25` on JDK 26 does not make the Java 26 runtime an officially supported Boot 3 configuration.

**Sources**: [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [current Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)

**Implementation consequence**: Stop feature implementation if the compatibility gate reveals linkage, instrumentation, build-plugin, or runtime incompatibility. The next action is a proposed constitution amendment, not a silent version substitution.

## 2. Maven Reactor and Deployable Boundaries

**Decision**: Create a root Maven reactor with the Maven Wrapper and exactly two application modules: `delivery-orchestrator` and `provider-simulator`. Centralize Java level, dependency management, test execution, static analysis, formatting, and dependency checks in the root. Do not create a shared domain or provider-contract Java module.

**Rationale**: The two modules are independently deployable, match the README and MVP, and can still be built with one `./mvnw verify`. Treating the simulator as an external system prevents its DTOs from leaking into the orchestrator domain and proves the adapter boundary is real.

**Alternatives considered**:

- One application module blurs deployable boundaries.
- A shared provider-contract module creates compile-time coupling to a simulator that stands in for replaceable external providers.
- Separate repositories add operational overhead without current value.

**Source**: [Maven guide to multiple modules](https://maven.apache.org/guides/mini/guide-multiple-modules.html)

## 3. Provider HTTP Access and Isolation

**Decision**: Give every configured provider adapter its own Spring `RestClient`, base URL, connection timeout, response timeout, bounded `ThreadPoolBulkhead`, provider identifier, and telemetry tags. Compose provider calls functionally so execution order is visible. Do not enable implicit client retries in this feature.

**Rationale**: `RestClient` is a synchronous, thread-safe client suited to blocking provider calls. Provider-specific clients and bulkheads prevent one provider from consuming another provider's connection, queue, execution, or telemetry budget. Functional composition prepares for the explicit timeout/retry/circuit/fallback sequence in feature 002.

**Alternatives considered**:

- One shared executor is globally bounded but does not isolate providers.
- A virtual-thread-per-task executor still needs explicit per-provider admission bounds and is unnecessary for three providers.
- `WebClient` adds a reactive model that the endpoint and domain do not otherwise need.
- OpenFeign adds another compatibility surface and obscures some client construction details.
- `RestTemplate` is an older API than `RestClient`.

**Sources**: [Spring REST clients reference](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html), [Resilience4j Spring Boot 3 guide](https://resilience4j.readme.io/docs/getting-started-3), [Oracle virtual-thread guidance](https://docs.oracle.com/en/java/javase/26/core/virtual-threads.html)

## 4. Externalized and Validated Configuration

**Decision**: Bind immutable validated configuration groups for ranking and providers. Ranking includes currency, score scale/precision, and ETA/price/reliability weights. Every provider includes canonical ID, active flag, base URL, reliability, client timeouts, and finite bulkhead core size, maximum size, queue capacity, and keep-alive. Startup validation enforces unique IDs, valid URLs, positive bounds/timeouts, reliability in `[0,100]`, non-negative weights, and an exact weight total of `100`.

**Rationale**: Cross-field startup validation prevents an invalid deployment from returning plausible but incorrect rankings. External configuration permits weight, provider, and environment changes without changing ranking code.

**Alternatives considered**:

- Hard-coded weights violate the constitution.
- Scattered value injection weakens cross-field validation and makes configuration tests harder.
- Runtime fallback to default weights would hide operational mistakes.

**Source**: [Spring Boot externalized configuration and validation](https://docs.spring.io/spring-boot/reference/features/external-config.html)

## 5. Provider-Neutral Domain Boundary

**Decision**: The orchestrator owns an outbound `DeliveryQuoteProvider` port using provider-neutral request and quote types. Each adapter exclusively owns provider request/response DTOs, HTTP details, unit conversion, status mapping, and correlation propagation. Provider identity and ranking reliability come from trusted orchestrator configuration rather than provider payloads.

**Rationale**: The scoring engine should know only `ProviderId`, `Money`, canonical estimated minutes, reliability, and ranking weights. This makes adapters replaceable and prevents a provider from self-reporting the reliability value used to rank itself.

**Alternatives considered**:

- Passing external DTOs into scoring couples business rules to provider schemas.
- A switch-based universal client prevents independent configuration, policies, and telemetry.
- Trusting provider-reported identity or reliability weakens deterministic configuration and ranking integrity.

## 6. Monetary and Time Representation

**Decision**: Represent money as a decimal amount plus configured ISO 4217 currency and estimated delivery time as `Duration` internally. Validate request values against the currency's minor-unit precision and reject excessive scale rather than silently rounding. Adapters convert provider time into `Duration`; the public contract uses positive whole `estimatedMinutes`, rounding finer durations upward. Ranking and tie-breaking use the same displayed canonical minutes.

**Rationale**: Decimal arithmetic preserves monetary meaning and deterministic comparison. A request-level currency snapshot prevents configuration changes during one operation. Ceiling to whole minutes avoids promising a shorter delivery time than the provider supplied.

**Alternatives considered**:

- Binary floating point can produce non-deterministic monetary comparisons.
- Integer minor units are exact but would break the established decimal HTTP contract.
- Milliseconds are precise but not the restaurant-facing unit established by the feature.

**Sources**: [Java arbitrary-precision decimal arithmetic](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/math/package-summary.html), [Java currency metadata](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/Currency.html)

## 7. Scoring and Deterministic Ranking

**Decision**: Snapshot valid quotes, configured reliability values, and weights before scoring. Reliability is already a decimal value in `[0,100]`. Normalize lower-is-better ETA and fee values across the eligible quote set:

```text
lowerBetter(value) =
  100                                  when maximum == minimum
  100 * (maximum - value)
      / (maximum - minimum)             otherwise

rawScore =
    lowerBetter(estimatedMinutes) * etaWeight
  + lowerBetter(fee)             * priceWeight
  + reliability                  * reliabilityWeight

publicScore = round(rawScore / 100, 2, HALF_UP)
```

Use decimal arithmetic with an explicit high-precision division context and round only the public score. Sort by public score descending, canonical estimated minutes ascending, exact fee ascending, and canonical provider ID ascending using case-sensitive ASCII lexical order. Provider IDs match `[A-Z][A-Z0-9_]{0,63}`. The first option is the sole recommendation.

**Rationale**: Min-max normalization is unitless, bounded, interpretable, and does not require unstated absolute thresholds. Giving every option `100` when a factor has no range makes that factor neutral for ordering. Sorting by the public rounded score prevents a hidden precision difference from contradicting a visible tie.

**Alternatives considered**:

- Ratio-to-best scoring behaves nonlinearly and degenerates at zero fee.
- Fixed thresholds require business targets absent from the specification.
- Z-scores are unbounded and difficult for stakeholders to interpret.
- Input-order tie-breaking depends on configuration or response timing and is not deterministic enough.

## 8. Public and Internal HTTP Contracts

**Decision**: Publish OpenAPI 3.1 documents for the public `POST /api/v1/delivery-options` operation and internal `POST /api/v1/quotes` provider operation. Accept an optional `X-Correlation-Id` header only. A valid caller value is preserved; otherwise a UUIDv4 is generated before validation. Return it in the body and response header, propagate it to providers, and keep it distinct from W3C trace context.

A successful feature-001 result uses `partialResult: false`, `unavailableProviders: []`, at least one option, and `recommendedProvider` equal to the first option. Validation uses `400 application/problem+json` following RFC 9457, extended with `correlationId` and a deterministically ordered `errors` array using JSON Pointers. Malformed JSON may produce one parser-level error. Invalid ranking configuration is a controlled `500` problem; `503` remains feature 002's all-provider-unavailable status.

**Rationale**: The contracts match the README, make currency explicit, and remain forward-compatible with constitution-required partial results. RFC 9457 provides a standard extensible error shape and permits validation-error extensions.

**Alternatives considered**:

- Putting correlation ID in both request body and header creates two authorities.
- Reusing the distributed trace ID cannot guarantee exact caller-value preservation.
- A bespoke error envelope loses standardized semantics and tooling.
- `422` adds a validation-status distinction with little client value for this operation.

**Sources**: [OpenAPI Specification](https://spec.openapis.org/oas/v3.1.0), [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html), [W3C Trace Context](https://www.w3.org/TR/trace-context/)

## 9. Test and Acceptance Strategy

**Decision**: Use focused unit tests for validation aggregation, score normalization/rounding, weight validation, sorting, recommendation, money/time conversion, and correlation behavior. Use MockMvc for the public contract, WireMock per provider adapter for HTTP mapping and three-provider orchestration, a latch/barrier fake-adapter test for concurrent fan-out, and Docker Compose plus curl for packaged end-to-end validation. Avoid time-only concurrency unit tests.

Measure SC-005 after 20 warm-up calls with 200 valid measured requests at concurrency 10 against one orchestrator and three NORMAL simulator instances. Require every response to be complete and correct and at least 95% to finish within 2 seconds. Record environment and p50/p95/p99.

**Rationale**: This combination proves business behavior at the cheapest layer while still exercising real HTTP and packaged deployables. The fixed workload resolves the specification's previously open acceptance workload without turning a local benchmark into a universal capacity claim.

**Alternatives considered**:

- Mockito-only tests do not verify provider HTTP mapping.
- Full Compose tests for every case are slower and harder to diagnose.
- JMH measures code paths rather than the restaurant-visible operation.
- Single-stopwatch assertions are noisy and provide no percentile evidence.

**Source**: [Spring Boot application testing](https://docs.spring.io/spring-boot/3.5/reference/testing/spring-boot-applications.html)

## 10. Observability for the Successful Slice

**Decision**: Emit structured JSON events for validation rejection, provider quote completion, ranking completion, and request completion. Include correlation ID, opaque restaurant/order identifiers, provider when applicable, outcome, option count, recommendation, completeness, configuration version, and duration; exclude bodies, coordinates, order value, and secrets. Use bounded metric tags only: operation outcome/completeness, provider/outcome, and validation field. Never use correlation, restaurant, or order identifiers as metric tags.

**Rationale**: This reveals whether the operation is useful, which provider participated, and whether latency/ranking behavior is healthy without leaking sensitive data or creating high-cardinality metrics.

**Alternatives considered**:

- Free-text logs are difficult to validate and query consistently.
- Logging request bodies violates the constitution's sensitive-data rule.
- Business identifiers as metric tags create unbounded series growth.

## 11. Persistence

**Decision**: Add no database, cache, or message broker. Quotes and results live only for one request; provider reliability is configuration until a later feature defines another source.

**Rationale**: Persistent order management, PostgreSQL, Redis, and Pub/Sub are expressly deferred and provide no value to this successful quote-comparison slice.

**Alternatives considered**: Persisting quotes or orders would expand scope, create retention questions, and violate the MVP sequencing without satisfying a current requirement.
