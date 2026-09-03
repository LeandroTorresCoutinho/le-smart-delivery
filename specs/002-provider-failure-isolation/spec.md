# Feature Specification: Provider Failure Isolation

**Feature Branch**: `main` (no branch hook configured)

**Created**: 2026-09-01

**Status**: Draft

**Input**: User description: "Keep delivery-option requests useful when one or more delivery providers are slow, rate-limited, temporarily unavailable, or persistently failing. Return ranked valid quotes whenever at least one provider succeeds, isolate failures by provider, report partial results explicitly, and return a controlled service-unavailable response when every provider is unavailable."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receive Useful Partial Results (Priority: P1)

As a restaurant, I receive ranked delivery options from every provider that returned a valid quote even when another configured provider fails, responds too slowly, is rate-limited, or has no drivers. I can see that the result is partial, which providers did not contribute an available quote, and which valid provider is recommended.

**Why this priority**: Continuing to accept orders during an individual provider disruption is the feature's primary customer value and is independently useful without provider recovery automation.

**Independent Test**: Submit a valid delivery-options request while at least one configured provider returns a valid quote and at least one other provider produces each supported unavailable outcome. Verify that all valid quotes are ranked, the highest-ranked valid quote is recommended, the result is marked partial, unavailable providers are identified, and the request correlation identifier is preserved.

**Acceptance Scenarios**:

1. **Given** two providers return valid quotes and one provider exceeds its allowed response time, **When** a restaurant requests delivery options, **Then** the two valid quotes are returned in normal rank order by the overall response deadline, the best valid quote is recommended, the result is marked partial, and the slow provider is listed as unavailable.
2. **Given** one provider reports no available drivers and another provider returns a valid quote, **When** delivery options are requested, **Then** the valid quote is returned and recommended, the result is marked partial, the no-driver provider is listed as unavailable, and that business outcome is not treated as a technical failure.
3. **Given** one provider returns a deterministic request error and another provider returns a valid quote, **When** delivery options are requested, **Then** the failing provider is attempted exactly once, the valid quote remains available, and the response does not expose internal failure details.
4. **Given** every configured provider returns a valid quote, **When** delivery options are requested, **Then** all quotes are ranked, the highest-ranked quote is recommended, the result is not marked partial, and the unavailable-provider list is empty.

---

### User Story 2 - Recover From Transient Provider Failures (Priority: P2)

As a restaurant, I benefit when a provider recovers from a transient failure within the request deadline, without waiting indefinitely or losing quotes from healthy providers.

**Why this priority**: Bounded recovery improves quote choice during brief disruptions while preserving the P1 guarantee that healthy providers remain useful.

**Independent Test**: Exercise each retryable and non-retryable provider outcome with observable attempt counts and timing. Verify that transient recovery can contribute a quote, non-retryable outcomes are attempted once, and no provider exceeds three attempts or the overall request deadline.

**Acceptance Scenarios**:

1. **Given** a provider experiences one retryable connection failure and succeeds on its next permitted attempt, **When** delivery options are requested, **Then** its successful quote is included and ranked without exceeding the provider attempt limit or the overall request deadline.
2. **Given** a provider exceeds its allowed response time on an attempt and a retry remains permitted, **When** delivery options are requested, **Then** waiting for that attempt stops, any permitted retry remains bounded, and healthy-provider quotes remain available by the overall response deadline.
3. **Given** a provider is rate-limited, **When** the active policy permits another attempt within both the attempt and response-time limits, **Then** the provider may be attempted again with an increasing, varied delay while healthy-provider quotes continue independently.
4. **Given** a provider returns a validation error, deterministic client error, permanent failure, or no-driver outcome, **When** delivery options are requested, **Then** the provider is attempted exactly once for that request.

---

### User Story 3 - Isolate Persistent Failure and Recovery (Priority: P3)

As a restaurant, I receive prompt delivery options while a persistently failing provider is temporarily excluded, and that provider can safely return to service after recovery without affecting other providers.

**Why this priority**: Persistent-failure isolation protects repeated requests and reduces unnecessary waiting, while controlled recovery restores provider choice after the immediate partial-result behavior is established.

**Independent Test**: Cause one provider to reach its qualifying failure threshold, verify subsequent requests do not contact it, allow its recovery interval to pass, and verify both successful and failed recovery probes without changing any healthy provider's state or availability.

**Acceptance Scenarios**:

1. **Given** one provider reaches its qualifying technical-failure threshold, **When** further delivery-options requests are made, **Then** only that provider becomes unavailable without contact and healthy providers continue receiving requests.
2. **Given** a provider is in the open state, **When** a restaurant requests delivery options before the recovery interval ends, **Then** no call reaches that provider and valid healthy-provider quotes are returned without waiting for the unavailable provider's timeout.
3. **Given** an open provider has recovered and its recovery interval has elapsed, **When** a bounded recovery probe succeeds, **Then** the provider returns to normal eligibility for subsequent requests.
4. **Given** an open provider remains unhealthy after its recovery interval, **When** a permitted recovery probe fails, **Then** the provider returns to the open state and other providers remain unaffected.
5. **Given** all configured providers are technically unavailable, have no available drivers, or are excluded by an open state, **When** delivery options are requested, **Then** the restaurant receives a controlled service-unavailable problem response containing the request correlation identifier and no internal failure details.

### Edge Cases

- A provider succeeds on the final permitted attempt just before the overall request deadline; its valid quote is included if it can be ranked before the response is finalized.
- A retry delay would extend beyond the remaining request time; that retry is not started.
- A provider produces a late response after its allowed time or after the overall result has been finalized; the late outcome does not alter the returned response.
- Multiple providers fail in different ways during the same request; every provider that does not contribute an available quote is listed once, while each provider's retry and circuit decisions remain independent.
- All providers report no drivers; the restaurant receives the controlled all-unavailable response, while none of those outcomes count toward technical circuit failure.
- A provider's recovery eligibility changes while concurrent restaurant requests are in progress; the number of recovery probes remains bounded and other requests fail fast for that provider.
- Duplicate or malformed provider quotes are not treated as valid available quotes and cannot become the recommendation.
- A provider is removed from or added to the configured provider set while requests are in flight; each request is evaluated against the provider set established when that request began.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The platform MUST request quotes from all providers configured for a delivery-options request without requiring one provider's outcome before beginning another provider's request.
- **FR-002**: Provider quote concurrency, pending work, attempt counts, per-provider waiting time, and the overall delivery-options response time MUST each have explicit finite bounds.
- **FR-003**: Each provider MUST have an independently applicable response-time limit, retry policy, and circuit state.
- **FR-004**: A timeout, retry, or circuit-state change for one provider MUST NOT consume or change another provider's allowance or circuit state.
- **FR-005**: Provider outcomes MUST be classified as valid quote, business unavailability, retryable technical failure, non-retryable technical failure, or temporarily excluded by circuit state.
- **FR-006**: Retryable conditions MUST be limited to connection failures, provider timeouts, rate-limit responses when the active policy permits retry, and explicitly approved transient service failures.
- **FR-007**: Validation failures, deterministic client errors, business unavailability such as no available drivers, and failures classified as permanent MUST NOT be retried.
- **FR-008**: The default provider policy MUST allow no more than three total attempts for one provider within a delivery-options request.
- **FR-009**: Every retry MUST remain within both the provider attempt limit and the overall delivery-options request deadline; a retry that cannot fit within the remaining time MUST NOT begin.
- **FR-010**: Delays between permitted retry attempts MUST increase and include randomized variation to reduce synchronized retry demand.
- **FR-011**: Repeated qualifying technical failures MUST open only the affected provider's circuit state.
- **FR-012**: While a provider's circuit is open, new quote requests MUST be rejected for that provider without contacting it.
- **FR-013**: After the configured recovery interval, the platform MUST permit a bounded number of recovery probes for the affected provider.
- **FR-014**: Successful recovery probes MUST restore the affected provider to normal request eligibility; failed probes MUST return it to the open state.
- **FR-015**: Business-unavailability outcomes MUST NOT count toward a provider's technical circuit-failure threshold.
- **FR-016**: When at least one provider returns a valid quote, the platform MUST return every valid quote received within the response deadline and rank them using the normal ranking policy.
- **FR-017**: The recommended option MUST be the highest-ranked valid available quote and MUST never be selected from a provider that failed, was excluded, or reported business unavailability.
- **FR-018**: When at least one configured provider does not contribute an available quote and at least one valid quote is returned, the response MUST set `partialResult` to `true` and list each non-contributing provider exactly once.
- **FR-019**: When every configured provider contributes a valid quote, the response MUST set `partialResult` to `false` and provide an empty unavailable-provider list.
- **FR-020**: When no provider contributes a valid quote, the platform MUST return a controlled service-unavailable problem response rather than an empty successful result or an unhandled failure.
- **FR-021**: Every partial or all-unavailable response MUST preserve the delivery-options request's correlation identifier.
- **FR-022**: Restaurant-facing responses MUST distinguish an available result from an all-unavailable problem without exposing exception messages, infrastructure identifiers, internal state transitions, or other implementation details.
- **FR-023**: Diagnostic records MUST identify the affected provider, outcome classification, attempt activity, relevant circuit status, correlation identifier, and whether useful results were returned, while excluding sensitive restaurant data and internal details from restaurant-facing responses.
- **FR-024**: A provider response received after its applicable deadline or after response finalization MUST NOT change the already finalized delivery-options result.

### Key Entities *(include if feature involves data)*

- **Delivery-options request**: A restaurant's valid request for ranked delivery choices; establishes the correlation identifier, overall response deadline, and configured provider set used for that request.
- **Provider quote outcome**: The result of one provider's participation, including provider identity, outcome classification, attempt count, timing status, and either a valid quote or an unavailable reason suitable for client reporting.
- **Valid quote**: An available delivery option eligible for the normal ranking policy, including the provider identity and the existing price, estimated-time, and reliability information used by that policy.
- **Delivery-options result**: The ranked set of valid quotes, recommended quote, partial-result indicator, unavailable-provider list, and correlation identifier.
- **Provider circuit state**: The independent availability state for one provider, including normal eligibility, temporary exclusion, recovery eligibility, and the bounded recovery-probe outcome.
- **Service-unavailable problem**: The controlled result returned when no provider contributes a valid quote; includes the correlation identifier and safe client-facing problem information.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of defined scenarios where at least one provider returns a valid quote within the request deadline, the restaurant receives ranked delivery options and a recommendation rather than a total-failure response.
- **SC-002**: In 100% of defined single-provider failure scenarios, every healthy provider remains able to contribute a quote and the restaurant receives the result no later than the configured overall response deadline.
- **SC-003**: Partial-result status and the unavailable-provider list are correct for 100% of the defined mixed-success, business-unavailability, and technical-failure scenarios.
- **SC-004**: Non-retryable outcomes result in exactly one provider attempt, while retryable outcomes never exceed three total attempts under the default policy or continue beyond the overall request deadline.
- **SC-005**: In 100% of open-circuit scenarios, zero outbound quote calls reach the affected provider before an allowed recovery probe, and no other provider's circuit state changes as a consequence.
- **SC-006**: Business-unavailability outcomes contribute zero events toward technical circuit opening in every defined no-driver scenario.
- **SC-007**: Both successful and failed provider recovery are demonstrated within the configured probe bound, with successful recovery restoring normal eligibility and failed recovery preserving fast exclusion.
- **SC-008**: In 100% of defined all-provider-unavailable scenarios, the restaurant receives a controlled service-unavailable problem with the correct correlation identifier and no exposed internal failure details.
- **SC-009**: Under the agreed peak request volume and the complete supported failure matrix, provider concurrency, queued work, and retry activity remain within their configured limits while useful responses continue meeting the overall request deadline.
- **SC-010**: In stakeholder validation, at least 95% of participating restaurant or support users can correctly identify the recommended available option, whether a response is partial, and which providers were unavailable from the returned information.

## Assumptions

- Quote requests are read-like operations and are safe to retry under the bounded policy; delivery creation and dispatch are separate operations and are not covered by this feature.
- The existing ranking policy remains authoritative and already produces one recommendation from valid available quotes.
- The provider set and applicable resilience policies are established when a delivery-options request begins and remain stable for that request.
- The overall response deadline, provider time limits, retry permissions, failure thresholds, recovery interval, and probe bound are supplied by existing platform configuration; this specification defines their required behavior rather than their numeric values, except for the default maximum of three attempts.
- A provider that reports no available drivers has participated successfully at a technical level but has no quote eligible for ranking.
- Failure diagnostics are available to authorized operational users through existing observability capabilities; this feature does not add an operational dashboard.

### Dependencies

- Existing ranked delivery options, including the normal ranking policy and recommendation selection.
- Existing provider scenario control capable of producing success, timeout, connection failure, rate-limit, service failure, client error, validation error, and no-driver outcomes.
- Existing correlation-identifier propagation and controlled problem-response conventions.

### Out of Scope

- Cached quote fallback.
- Delivery creation, dispatch, or retry of non-idempotent delivery creation.
- Persistence of provider circuit state across platform restarts.
- Real delivery-provider integrations.
- Restaurant-specific resilience policies.
- Operational dashboards, cloud deployment, authentication, or tenant isolation.

