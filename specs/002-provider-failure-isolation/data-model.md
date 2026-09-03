# Phase 1 Data Model: Provider Failure Isolation

## Modeling Principles

- Extend the provider-neutral request, quote, option, money, location, correlation, ranking, and validation models defined by feature 001; provider HTTP payloads still terminate at adapter boundaries.
- Snapshot the active provider set, ranking configuration, resilience policy, correlation identifier, and monotonic deadline once per delivery-options request.
- Represent every configured provider with exactly one immutable terminal participation outcome, whether it returned a valid quote or became unavailable.
- Keep attempt details and technical reasons internal. Restaurant-facing success responses expose provider IDs only; problem responses never expose exception or infrastructure details.
- Make aggregation finalization single-assignment. A late completion may be observed diagnostically but cannot modify ranking, response type, or returned-result metrics.
- Keep circuit state process-local, independently keyed by provider, and non-persistent.

## Entities and Value Objects

### DeliveryOptionsExecutionContext

Immutable context created after request validation and before provider fan-out.

| Field | Type | Required | Validation and meaning |
|---|---|---:|---|
| `correlationId` | `CorrelationId` | Yes | Same safe identifier used in response header/body, provider calls, logs, and traces. |
| `startedAt` | Monotonic time point | Yes | Captured from an injectable ticker; never used as wall-clock business time. |
| `deadline` | Monotonic time point | Yes | Strictly after `startedAt`; common upper bound for all provider work and finalization. |
| `finalizationReserve` | Duration | Yes | Positive finite time reserved for outcome snapshotting, scoring, ranking, mapping, and serialization. |
| `providerSnapshot` | Ordered list of `ProviderExecutionSnapshot` | Yes | At least one active provider; IDs unique and canonically ordered. |
| `rankingSnapshot` | Existing `RankingConfiguration` | Yes | Same immutable ranking rules used for every valid quote in this request. |
| `finalizationState` | `COLLECTING | FINALIZED` | Yes | Single-assignment guard; transitions at most once. |

### ProviderExecutionSnapshot

One request-scoped view of a configured provider.

| Field | Type | Required | Validation and meaning |
|---|---|---:|---|
| `providerId` | `ProviderId` | Yes | Canonical configured ID matching feature 001 rules. |
| `adapter` | Provider-neutral outbound port reference | Yes | Selects the provider-specific adapter without exposing its DTOs to the domain. |
| `reliability` | Decimal percentage | Yes | Snapshotted scoring input in `[0,100]`. |
| `policy` | `ProviderResiliencePolicy` | Yes | Immutable provider-specific policy snapshot. |
| `circuit` | Provider circuit identity | Yes | Stable process-local instance dedicated to this provider. |

### ProviderResiliencePolicy

Validated finite settings applied independently to one provider.

| Field | Type | Validation and meaning |
|---|---|---|
| `connectTimeout` | Duration | Positive and finite; lower-level network connection bound. |
| `responseTimeout` | Duration | Positive and finite; lower-level provider response bound. |
| `logicalTimeout` | Duration | Positive and finite; includes admission, attempts, and backoff and must fit before the request deadline reserve. |
| `maxAttempts` | Integer | Inclusive range `1..3`; includes the initial call. |
| `initialBackoff` | Duration | Positive, finite, and short enough to permit a bounded retry in the local profile. |
| `backoffMultiplier` | Decimal | At least `1`; default greater than `1` for exponential increase. |
| `maximumBackoff` | Duration | At least `initialBackoff` and finite. |
| `jitterMinimum` / `jitterMaximum` | Decimal factors | Non-negative bounded range with minimum not greater than maximum; selected values must not make later retry windows regress. |
| `retryableStatuses` | Bounded status set | Defaults to `408`, `429`, `500`, `502`, `503`, and `504`; other `5xx` are not implicitly retryable. |
| `honorRetryAfter` | Boolean | Controls `Retry-After` use for permitted `429`/`503` responses. |
| `bulkheadCoreSize` | Integer | Positive finite provider-local bound. |
| `bulkheadMaxSize` | Integer | At least core size and finite. |
| `bulkheadQueueCapacity` | Integer | Non-negative and finite. |
| `bulkheadKeepAlive` | Duration | Non-negative and finite. |
| `circuitWindowSize` | Integer | Positive finite count window. |
| `circuitMinimumCalls` | Integer | Positive and no greater than window size. |
| `circuitFailureThreshold` | Percentage | In `(0,100]`. |
| `circuitOpenDuration` | Duration | Positive finite recovery interval. |
| `halfOpenPermits` | Integer | Positive finite probe bound; initial MVP default `2`. |
| `halfOpenMaximumDuration` | Duration | Positive finite duration longer than one probe attempt. |

Cross-field validation rejects a policy whose maximum attempt/backoff envelope can never leave the configured finalization reserve within the request deadline. Environment-specific values remain configuration; the default policy never exceeds three attempts.

### ProviderAttempt

Internal record for one actual outbound attempt.

| Field | Type | Required | Validation and meaning |
|---|---|---:|---|
| `ordinal` | Integer | Yes | Inclusive range `1..maxAttempts`; no gaps or duplicates within one participation. |
| `startedAt` | Monotonic time point | Yes | Must be earlier than the immutable request deadline. |
| `completedAt` | Monotonic time point | Terminal attempts | Used to distinguish in-deadline from late completion. |
| `sourceOutcome` | `ProviderSourceOutcome` | Terminal attempts | Normalized result of the provider HTTP exchange or transport. |
| `retryDecision` | `NOT_APPLICABLE | SCHEDULED | SKIPPED_NON_RETRYABLE | SKIPPED_DEADLINE | EXHAUSTED` | Yes | Exactly explains why another attempt did or did not start. |
| `delayBeforeNextAttempt` | Duration | When scheduled | Non-negative, bounded, and computed from exponential backoff, jitter, and valid `Retry-After`. |

Attempt records are diagnostic and test evidence; they are never returned to restaurants.

### ProviderSourceOutcome

Canonical classifier input/output categories.

| Category | Meaning | Retryable | Circuit contribution after logical completion |
|---|---|---:|---:|
| `VALID_QUOTE` | Well-formed available provider quote | No | Success |
| `NO_DRIVERS` | Valid business unavailability | No | Ignored |
| `CONNECTION_FAILURE` | Transient connection refusal/reset or connect timeout | Yes | Failure if exhausted |
| `RESPONSE_TIMEOUT` | Provider did not complete the attempt in time | Yes | Failure if exhausted |
| `RATE_LIMITED` | Permitted `429` outcome | Policy/deadline dependent | Failure if exhausted |
| `TRANSIENT_SERVICE_FAILURE` | Allowlisted transient `408`/`5xx` | Yes | Failure if exhausted |
| `DETERMINISTIC_CLIENT_ERROR` | Provider validation or other deterministic `4xx` | No | Ignored |
| `PERMANENT_LOCAL_FAILURE` | TLS/hostname/URL/configuration problem | No | Ignored and alerted |
| `INVALID_PROVIDER_RESPONSE` | Malformed or invalid successful payload | No | Failure |
| `CIRCUIT_REJECTED` | Open or excess half-open circuit rejected before contact | No | No new sample |
| `LOCAL_CAPACITY_REJECTED` | Provider bulkhead or executor rejected admission | No | Ignored |
| `DEADLINE_EXHAUSTED` | No safe attempt could begin before finalization | No | Ignored |
| `CANCELLED` | Orchestration ended before provider work was used | No | Permission released if acquired but unused |

### ProviderParticipation

Exactly one terminal provider-neutral outcome per snapshotted provider.

| Field | Type | Required | Validation and meaning |
|---|---|---:|---|
| `providerId` | `ProviderId` | Yes | Unique within the delivery-options execution. |
| `classification` | `AVAILABLE | BUSINESS_UNAVAILABLE | TECHNICAL_UNAVAILABLE | CIRCUIT_EXCLUDED | LOCAL_CAPACITY | DEADLINE_EXHAUSTED | CANCELLED` | Yes | Safe terminal classification. |
| `quote` | Existing `ProviderQuote` | Only for `AVAILABLE` | Mutually exclusive with an unavailable outcome. |
| `internalReason` | `ProviderSourceOutcome` | For unavailable outcomes | Diagnostic classification, never serialized to the restaurant success body. |
| `attempts` | Ordered list of `ProviderAttempt` | Yes | Size `0..3`; open circuit and pre-contact deadline exhaustion use zero attempts. |
| `completedAt` | Monotonic time point | Yes | Determines eligibility for the final snapshot. |
| `circuitDisposition` | `SUCCESS | FAILURE | IGNORED | REJECTED | RELEASED` | Yes | Proves exactly one logical accounting decision. |
| `returnedUsefully` | Boolean | After request finalization | True when at least one valid quote made the restaurant response useful; diagnostic only. |

Every participation contains either one valid quote or one unavailability classification, never both. Retry attempt failures are folded into one terminal participation and therefore contribute at most one circuit health sample.

### ProviderCircuitState

Process-local state associated with one provider ID.

| Field | Type | Validation and meaning |
|---|---|---|
| `state` | `CLOSED | OPEN | HALF_OPEN` | Normal operating states used by this feature. |
| `openedAt` | Monotonic time point | Present while open; determines recovery eligibility. |
| `probePermits` | Integer | Bounded by `halfOpenPermits`; never shared with another provider. |
| `halfOpenEnteredAt` | Monotonic time point | Present while half open; finite maximum duration prevents indefinite recovery state. |

Metrics-only, disabled, and forced-open administrative library states are not part of restaurant-facing domain behavior and require separate operational controls if introduced later.

### DeliveryOptionsResult

Generalizes feature 001's complete result without changing its fields.

| Field | Type | Required invariant |
|---|---|---|
| `correlationId` | `CorrelationId` | Always present and matches the response header. |
| `currency` | ISO 4217 code | Matches every valid option fee. |
| `options` | Ordered list of `DeliveryOption` | At least one; provider IDs unique; existing deterministic ranking order. |
| `recommendedProvider` | `ProviderId` | Exactly equals `options[0].provider`. |
| `unavailableProviders` | Ordered list of `ProviderId` | Unique, canonical order, and disjoint from option providers. |
| `partialResult` | Boolean | True if and only if `unavailableProviders` is non-empty. |

The option-provider set and unavailable-provider set partition the immutable provider snapshot exactly. A complete feature-001-compatible result has all providers in `options`, an empty unavailable list, and `partialResult=false`.

### ProviderUnavailableProblem

RFC 9457 problem returned when no provider contributes a valid quote.

| Field | Type | Required invariant |
|---|---|---|
| `type` | URI reference | Stable value `/problems/delivery-providers-unavailable`. |
| `title` | String | Stable safe category. |
| `status` | Integer | Always `503`. |
| `detail` | String | Safe retry-later guidance without provider internals. |
| `instance` | URI reference | Current operation path when available. |
| `correlationId` | `CorrelationId` | Matches the response header. |
| `unavailableProviders` | Ordered list of `ProviderId` | Contains every provider in the request snapshot exactly once. |

## Relationships

```text
DeliveryOptionsExecutionContext
  ├── snapshots RankingConfiguration
  └── contains ProviderExecutionSnapshot (1..n)
        └── owns ProviderResiliencePolicy + stable ProviderCircuitState
              └── produces ProviderParticipation (exactly 1)
                    ├── contains ProviderAttempt (0..3)
                    └── contains either ProviderQuote or unavailability

ProviderParticipation (1..n)
  ├── one or more valid quotes ──> scoring/ranking ──> DeliveryOptionsResult
  └── zero valid quotes ────────────────────────────> ProviderUnavailableProblem
```

## State Transitions

### Delivery-options execution

```text
RECEIVED
  ├── invalid ──> REJECTED ──> validation problem
  └── valid ────> SNAPSHOTTED ──> COLLECTING
                                      ├── one or more valid quotes
                                      │     └── SCORED ──> RANKED
                                      │           ├── no unavailable providers ──> COMPLETE_200
                                      │           └── unavailable providers ─────> PARTIAL_200
                                      └── zero valid quotes ──────────────────────> ALL_UNAVAILABLE_503
```

The immutable deadline forces `COLLECTING` to finalize. `COMPLETE_200`, `PARTIAL_200`, and `ALL_UNAVAILABLE_503` are terminal and mutually exclusive.

### Provider participation

```text
ELIGIBILITY_CHECK
  ├── circuit rejects ─────────────> CIRCUIT_EXCLUDED
  ├── deadline already insufficient > DEADLINE_EXHAUSTED
  └── circuit permission acquired ─> LOGICAL_TIME_LIMIT
                                       └── BULKHEAD_ADMISSION
                                             ├── rejected ──> LOCAL_CAPACITY
                                             └── admitted ──> ATTEMPT_RUNNING
                                                                 ├── valid quote ───────> AVAILABLE
                                                                 ├── no drivers ────────> BUSINESS_UNAVAILABLE
                                                                 ├── non-retryable ─────> TECHNICAL_UNAVAILABLE
                                                                 └── retryable failure
                                                                       ├── budget fits ─> RETRY_WAIT ─> ATTEMPT_RUNNING
                                                                       └── exhausted ───> TECHNICAL_UNAVAILABLE
```

### Provider circuit

```text
CLOSED
  └── qualifying logical failures reach threshold ──> OPEN
OPEN
  ├── recovery interval not elapsed ─────────────────> reject without provider contact
  └── interval elapsed and permit available ─────────> HALF_OPEN
HALF_OPEN
  ├── required one-attempt probes succeed ───────────> CLOSED
  ├── qualifying probe fails ────────────────────────> OPEN
  └── maximum half-open duration expires ────────────> OPEN
```

## Cross-Entity Invariants

- No provider pipeline begins until request validation succeeds and the execution context is snapshotted.
- All provider pipelines begin before the orchestrator waits for their results.
- Every snapshotted provider produces exactly one terminal participation, including zero-attempt circuit/deadline/local-capacity outcomes.
- The same provider ID cannot appear in both `options` and `unavailableProviders`.
- A `200` result requires at least one valid quote completed no later than the immutable deadline.
- Zero valid quotes always produce `ProviderUnavailableProblem`; an empty successful result is invalid.
- The recommendation is selected only after invalid/unavailable outcomes are removed and always equals the first ranked option.
- Non-retryable outcomes have at most one actual attempt. Circuit-open and pre-contact deadline outcomes have zero attempts.
- A retry cannot start unless its effective delay, a full next-attempt budget, and finalization reserve fit before the deadline.
- One provider participation contributes at most one terminal success/failure sample to that provider's circuit.
- Business unavailability, deterministic client errors, local capacity rejection, and pre-contact deadline exhaustion contribute zero provider circuit failures.
- Half-open probes have one attempt each and cannot exceed the configured concurrent permit count.
- Finalization is single-assignment. Late completions cannot change the response, ranking snapshot, or metrics describing what the restaurant received.
- Circuit, retry, bulkhead, and timeout state/budgets are never shared across provider IDs.
- No request, quote, outcome, attempt history, or circuit state is persisted after its applicable in-memory lifetime.
