# Phase 1 Data Model: Ranked Delivery Quotes

## Modeling Principles

- All domain values are immutable and request-scoped.
- Provider-specific HTTP payloads stop at adapter boundaries and never enter scoring.
- Monetary and scoring calculations use decimal arithmetic, never binary floating point.
- Provider identity, reliability, and active status come from validated trusted configuration.
- Correlation is established before request validation and follows the request through every outcome.
- This feature stores no order, quote, option, or result after the response completes.

## Entities and Value Objects

### DeliveryOptionsRequest

Represents the restaurant's request before provider fan-out.

| Field | Type | Required | Validation and meaning |
|---|---|---:|---|
| `restaurantId` | Opaque string | Yes | Trimmed nonblank value, 1–128 characters. Identifies the restaurant business context. |
| `orderId` | Opaque string | Yes | Trimmed nonblank value, 1–128 characters. Unique within the restaurant context. |
| `orderValue` | `Money` | Yes | Amount greater than zero, with no more fractional digits than the configured currency permits. |
| `pickup` | `GeoPoint` | Yes | Valid pickup coordinate pair. |
| `destination` | `GeoPoint` | Yes | Valid destination coordinate pair. |
| `correlationId` | `CorrelationId` | Yes after ingress | Preserved from one valid `X-Correlation-Id` value or generated before validation. |

The HTTP request does not carry currency. The use case snapshots the configured platform currency when constructing `orderValue`.

### CorrelationId

| Property | Rule |
|---|---|
| Caller-provided value | One header value matching `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`; preserved exactly. |
| Generated value | UUID version 4 text. |
| Propagation | Public response header and body, every provider request, and structured logs. |
| Invalid or duplicate caller value | Request is rejected; a newly generated safe identifier correlates the problem response. |

Distributed tracing identifiers are separate observability context and do not replace this business correlation value.

### GeoPoint

| Field | Type | Validation |
|---|---|---|
| `latitude` | Decimal | Inclusive range `[-90, 90]`. |
| `longitude` | Decimal | Inclusive range `[-180, 180]`. |

Both members are required. Coordinates are used for quote requests but are never emitted in logs or metric tags.

### Money

| Field | Type | Validation |
|---|---|---|
| `amount` | Arbitrary-precision decimal | Non-negative for a provider fee; strictly positive for order value. Scale must not exceed the currency's standard minor-unit scale. |
| `currency` | ISO 4217 currency | Snapshotted from validated ranking/platform configuration. |

All quotes in one result use the same currency. Numerically equal amounts such as `12.9` and `12.90` compare as equal. Excessive input scale is rejected rather than rounded silently.

### ProviderId

A canonical configured identifier matching `[A-Z][A-Z0-9_]{0,63}`. Active provider IDs are unique. Ordering uses case-sensitive ASCII lexical comparison so results do not depend on locale.

### ProviderConfiguration

Defines one trusted active-provider boundary.

| Field | Type | Validation and meaning |
|---|---|---|
| `id` | `ProviderId` | Unique canonical provider identifier. |
| `active` | Boolean | Only active providers participate in a request snapshot. |
| `baseUrl` | URI | Absolute internal provider endpoint base URL. |
| `reliability` | Decimal | Inclusive range `[0,100]`; supplied to scoring, never taken from provider payload. |
| `connectTimeout` | Duration | Strictly positive and finite. |
| `responseTimeout` | Duration | Strictly positive and finite. |
| `bulkheadCoreSize` | Integer | Strictly positive finite execution bound. |
| `bulkheadMaxSize` | Integer | At least core size and finite. |
| `bulkheadQueueCapacity` | Integer | Non-negative finite queue bound. |
| `bulkheadKeepAlive` | Duration | Non-negative finite value. |

Configuration validation also requires at least one active provider for this successful-flow feature.

### ProviderQuoteRequest

Provider-neutral outbound-port input. It contains restaurant ID, order ID, order value, pickup, destination, and correlation ID. Each adapter converts it to its own provider DTO. It contains no ranking weights or reliability value.

### ProviderQuote

Provider-neutral successful quote produced by an adapter.

| Field | Type | Validation and meaning |
|---|---|---|
| `providerId` | `ProviderId` | Taken from trusted adapter configuration, not provider response data. |
| `fee` | `Money` | Non-negative and in the request currency. |
| `estimatedDuration` | Duration | Strictly positive; converted to canonical whole minutes using ceiling. |

Feature 001 admits only a valid `ProviderQuote` into scoring. Technical and business-unavailability outcomes are modeled by feature 002 rather than represented as fake quotes.

### RankingConfiguration

Snapshotted once at request start.

| Field | Type | Validation and default |
|---|---|---|
| `etaWeight` | Decimal percentage | Non-negative; default `45`. |
| `priceWeight` | Decimal percentage | Non-negative; default `35`. |
| `reliabilityWeight` | Decimal percentage | Non-negative; default `20`. |
| `currency` | ISO 4217 currency | Initial environment default `BRL`. |
| `scoreScale` | Integer | Fixed public scale `2`. |
| `roundingMode` | Enum | Fixed `HALF_UP`. |
| `configurationVersion` | Bounded string | Stable identifier included in ranking telemetry. |

The exact sum of all three weights is `100`. Invalid configuration prevents application readiness or, if changed dynamically in a later feature, produces a controlled configuration problem rather than a recommendation.

### ScoringSnapshot

Immutable calculation input consisting of the complete list of eligible provider quotes, the provider reliability value for each quote, and one ranking configuration snapshot. The snapshot prevents mid-request configuration changes from producing internally inconsistent scores.

### DeliveryOption

The provider-neutral public comparison item.

| Field | Type | Validation and meaning |
|---|---|---|
| `provider` | `ProviderId` | Unique within the result. |
| `fee` | Decimal | Exact `Money.amount`; currency appears once at result level. |
| `estimatedMinutes` | Positive integer | Ceiling of the provider quote duration to whole minutes. |
| `score` | Decimal | Inclusive range `[0.00,100.00]`, always serialized with two fractional digits. |

Recommendation is a result-level invariant rather than a duplicated flag on each option.

### DeliveryOptionsResult

| Field | Type | Required invariant for feature 001 |
|---|---|---|
| `correlationId` | `CorrelationId` | Always present. |
| `currency` | ISO 4217 code | Matches every option fee and request order value. |
| `options` | Ordered list of `DeliveryOption` | At least one item; provider IDs unique; deterministic order. |
| `recommendedProvider` | `ProviderId` | Exactly equal to `options[0].provider`. |
| `unavailableProviders` | Ordered list of `ProviderId` | Empty in feature 001. |
| `partialResult` | Boolean | `false` in feature 001. |

The contract retains unavailable and partial-result fields so feature 002 can implement constitution-required failure behavior without changing successful clients.

### ValidationProblem

RFC 9457 problem details extended for deterministic field validation.

| Field | Type | Meaning |
|---|---|---|
| `type` | URI reference | Stable problem category. |
| `title` | String | Short human-readable category. |
| `status` | Integer | HTTP status, normally `400` for request validation. |
| `detail` | String | Corrective summary without rejected values or internals. |
| `instance` | URI reference | Current request path when available. |
| `correlationId` | `CorrelationId` | Safe caller value or generated replacement. |
| `errors` | Ordered list of `FieldViolation` | All detectable errors from a parseable request. |

### FieldViolation

| Field | Type | Validation and meaning |
|---|---|---|
| `code` | Canonical string | Bounded known value such as `REQUIRED`, `OUT_OF_RANGE`, `INVALID_SCALE`, or `INVALID_FORMAT`. |
| `detail` | String | Corrective message without echoing the rejected value. |
| `source.pointer` | JSON Pointer | Points to the request member, such as `#/pickup/latitude`. |

Violations are ordered by pointer, then code, so identical invalid requests produce identical problem bodies. Malformed JSON may yield one root parser violation because remaining fields cannot be inspected safely.

## Relationships

```text
DeliveryOptionsRequest
  ├── contains pickup and destination GeoPoint values
  ├── contains orderValue Money
  └── carries one CorrelationId

Active ProviderConfiguration (1..n)
  └── selects one DeliveryQuoteProvider adapter each
        └── converts ProviderQuoteRequest into one ProviderQuote

ProviderQuote (1..n) + RankingConfiguration + configured reliability
  └── form one ScoringSnapshot
        └── produces ordered DeliveryOption (1..n)
              └── compose one DeliveryOptionsResult

Invalid DeliveryOptionsRequest
  └── produces one ValidationProblem containing FieldViolation (1..n)
```

## Scoring Rules

For each candidate, normalize the lower-is-better canonical ETA and fee over all eligible quotes:

```text
lowerBetter(value, minimum, maximum) =
  100                                      if maximum == minimum
  100 * (maximum - value) / (maximum - minimum) otherwise

rawScore =
    lowerBetter(estimatedMinutes) * etaWeight
  + lowerBetter(fee)             * priceWeight
  + reliability                  * reliabilityWeight

publicScore = round(rawScore / 100, 2, HALF_UP)
```

Use a documented high-precision decimal calculation context and round only `publicScore`. A one-quote snapshot receives `100` for both relative ETA and price. Reliability remains its configured `[0,100]` value.

Order options by:

1. Public score descending.
2. Canonical estimated minutes ascending.
3. Exact fee amount ascending.
4. Provider ID ascending with case-sensitive ASCII lexical comparison.

The first option becomes the sole `recommendedProvider`.

## Validation Ordering

1. Establish or replace the correlation identifier safely.
2. Parse the request body.
3. Collect structural and semantic request violations.
4. If any violation exists, sort violations deterministically and return a problem without resolving or invoking providers.
5. Snapshot and validate ranking/provider configuration.
6. Proceed to concurrent quote collection only when all gates pass.

## Request Lifecycle

The lifecycle is transient and not persisted:

```text
RECEIVED
  ├── invalid header/body/fields ──> REJECTED ──> problem returned
  └── valid request ───────────────> VALIDATED
                                       └── configuration snapshot
                                             └── QUOTES_REQUESTED
                                                   └── all valid quotes received
                                                         └── SCORED
                                                               └── RANKED
                                                                     └── COMPLETED
```

Feature 001 defines only the all-valid-quotes transition from `QUOTES_REQUESTED`. Feature 002 adds provider-unavailable and total-failure transitions without changing the successful path.

## Cross-Entity Invariants

- No provider adapter is invoked unless request validation succeeds completely.
- Exactly one adapter is invoked for every active provider in the request snapshot.
- The set of result provider IDs equals the set of active provider IDs for feature 001.
- Every option uses the request's snapshotted currency and ranking configuration.
- `recommendedProvider` always equals the first provider in the sorted options list.
- `partialResult` is `false` if and only if `unavailableProviders` is empty for this successful-flow feature.
- Repeating identical request, quote, reliability, and ranking snapshots produces byte-equivalent canonical option ordering and equal scores.
