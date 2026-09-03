# Feature Specification: Deterministic Provider Scenario Control

**Feature Branch**: `main`

**Created**: 2026-09-01

**Status**: Draft

**Input**: User description: "Provide independent, deterministic control over simulated delivery-provider behavior for development, testing, review, and demonstrations."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure and Exercise Provider Scenarios (Priority: P1)

As a developer, tester, reviewer, or demo operator, I can assign a supported behavior scenario to one selected simulated provider so that its subsequent quote outcomes reproduce the condition I need to evaluate.

**Why this priority**: Configuring predictable normal, delayed, failure, rate-limit, and business-unavailability behavior is the feature's primary value and enables resilience work elsewhere in the product.

**Independent Test**: Select one provider, configure each supported scenario with valid parameters, request quotes, and verify that every observed outcome matches the configured scenario without restarting the provider.

**Acceptance Scenarios**:

1. **Given** a newly started provider, **When** its active scenario is inspected and a quote is requested, **Then** the scenario is `NORMAL` and the request returns a valid quote.
2. **Given** a provider in `NORMAL`, **When** an operator activates `SLOW` with a valid delay, **Then** each subsequent quote returns a valid quote after approximately that delay.
3. **Given** `TEMPORARY_ERROR` configured for two initial failures, **When** three quotes are requested in sequence, **Then** the first two produce service-unavailable outcomes and the third returns a valid quote.
4. **Given** the same `INTERMITTENT_ERROR` configuration and reset state, **When** the same-length quote sequence is repeated, **Then** the failure and success outcomes occur in the same order.
5. **Given** `ALWAYS_ERROR`, **When** any number of quotes are requested, **Then** every request produces a technical service-unavailable outcome.
6. **Given** `RATE_LIMITED`, **When** any number of quotes are requested, **Then** every request produces a rate-limit outcome.
7. **Given** `NO_DRIVERS`, **When** a quote is requested, **Then** the provider returns a valid business response indicating no driver availability rather than a technical failure.

---

### User Story 2 - Inspect and Reset Provider State (Priority: P2)

As an operator, I can inspect a provider's active scenario and progression state, restart its deterministic sequence, or restore normal behavior so that I always know the simulator's test preconditions.

**Why this priority**: Reliable inspection and reset make configured behavior understandable, repeatable, and practical across successive tests and demonstrations.

**Independent Test**: Configure a stateful scenario, consume part of its sequence, inspect its state, restart its counters, and finally restore `NORMAL`; verify the parameters and outcomes at every step.

**Acceptance Scenarios**:

1. **Given** a provider with an active parameterized scenario, **When** the operator inspects it, **Then** the provider reports its scenario, effective parameters, and non-sensitive progression counters.
2. **Given** a partially consumed deterministic scenario, **When** the operator restarts its counters, **Then** the scenario retains its parameters and subsequent quotes reproduce the sequence from its beginning.
3. **Given** a provider in any non-normal scenario, **When** the operator restores normal behavior, **Then** its scenario becomes `NORMAL`, scenario parameters are cleared, counters return to their defaults, and subsequent quotes return valid quotes.

---

### User Story 3 - Preserve Valid and Isolated State (Priority: P3)

As an operator, I receive understandable validation feedback and can configure one provider without disturbing another so that a mistake or targeted experiment cannot corrupt other test conditions.

**Why this priority**: Isolation and atomic validation prevent false test results and are required by the project's provider-boundary principles.

**Independent Test**: Give three providers distinct configurations and progressed counters, submit both invalid and valid changes to one provider, and verify that the prior valid state is retained after rejection while all non-selected providers remain unchanged.

**Acceptance Scenarios**:

1. **Given** a provider with an active valid scenario, **When** an unsupported scenario or invalid parameter is submitted, **Then** the change is rejected with an understandable reason and the previous scenario, parameters, and counters remain active.
2. **Given** three independently configured providers, **When** one provider's scenario changes or resets, **Then** the other two retain their scenarios, parameters, and counters.
3. **Given** a quote already in progress while a valid scenario change is accepted, **When** that quote completes, **Then** it follows the scenario captured when it began and the new scenario governs the next quote begun afterward.

---

### User Story 4 - Keep Administration Internal (Priority: P4)

As a system owner, I can make simulator controls available to trusted development, test, and demo operators without exposing configuration state or controls to public production users.

**Why this priority**: Scenario administration intentionally creates failures and must remain separated from the customer-facing product boundary.

**Independent Test**: In a production-style environment, attempt scenario configuration and inspection through both the public entry point and the designated internal path; verify that public access is unavailable and internal use exposes configuration state only.

**Acceptance Scenarios**:

1. **Given** a production-style deployment, **When** a caller uses the public entry point, **Then** no scenario administration capability is discoverable or accessible.
2. **Given** an authorized internal operator inspecting a scenario, **When** configuration state is returned, **Then** no customer request contents, secrets, or sensitive data are included.

### Edge Cases

- A delay at either valid boundary is accepted; a delay outside the range is rejected without changing active state.
- A temporary-failure count of zero causes the next and all later requests to succeed while still reporting the selected scenario and zero remaining failures.
- An intermittent explicit pattern containing only successes or only failures remains valid and repeats exactly as supplied.
- An intermittent percentage of 0 produces only successes, while 100 produces only failures; both remain reproducible after a counter restart.
- A configuration that supplies missing, conflicting, unsupported, malformed, or out-of-range parameters is rejected atomically.
- A configuration or reset targeting an unknown provider is rejected and does not create provider state.
- Concurrent quote requests receive distinct sequence positions in a single deterministic order; no position is skipped or consumed more than once.
- Replacing or restarting a provider creates a new provider instance in `NORMAL`; preserving scenario state across replacement or redeployment is out of scope.
- An accepted scenario change affects quotes begun afterward and does not retroactively change a quote already in progress.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow a trusted operator to select one existing simulated provider and activate exactly one of `NORMAL`, `SLOW`, `TEMPORARY_ERROR`, `INTERMITTENT_ERROR`, `ALWAYS_ERROR`, `RATE_LIMITED`, or `NO_DRIVERS`.
- **FR-002**: Every new or replaced provider instance MUST begin in `NORMAL` with no scenario parameters and zeroed progression counters unless a valid scenario is explicitly activated afterward.
- **FR-003**: A successfully activated scenario MUST govern every quote begun after activation until it is replaced or reset.
- **FR-004**: `NORMAL` MUST return a valid delivery quote without scenario-induced delay or failure.
- **FR-005**: `SLOW` MUST require a response delay from 0 through 30 seconds inclusive and MUST return a valid quote after approximately the configured delay.
- **FR-006**: `TEMPORARY_ERROR` MUST require an initial-failure count from 0 through 100 inclusive, produce a service-unavailable outcome for exactly that many sequential quotes, and return valid quotes thereafter.
- **FR-007**: `INTERMITTENT_ERROR` MUST accept exactly one deterministic method: either an explicit repeating success/failure pattern containing 1 through 100 positions, or a whole-number failure percentage from 0 through 100 paired with a non-empty reproducibility key of at most 64 characters.
- **FR-008**: `INTERMITTENT_ERROR` MUST reproduce the same ordered outcomes when the same configuration starts from reset state, including when quote requests overlap.
- **FR-009**: `ALWAYS_ERROR` MUST produce a technical service-unavailable outcome for every quote.
- **FR-010**: `RATE_LIMITED` MUST produce a rate-limit outcome for every quote.
- **FR-011**: `NO_DRIVERS` MUST produce a valid business-unavailability response indicating no drivers and MUST NOT represent that outcome as a technical failure.
- **FR-012**: `NORMAL`, `ALWAYS_ERROR`, `RATE_LIMITED`, and `NO_DRIVERS` MUST reject scenario-specific parameters; `SLOW`, `TEMPORARY_ERROR`, and `INTERMITTENT_ERROR` MUST reject missing, conflicting, unsupported, or out-of-range parameters.
- **FR-013**: The system MUST validate the provider identity, scenario name, parameter combination, parameter types, and parameter ranges before activating any change.
- **FR-014**: A rejected configuration MUST leave the provider's prior scenario, parameters, and progression counters unchanged and MUST identify each invalid field with an understandable reason.
- **FR-015**: The system MUST maintain scenario, parameters, and progression counters independently for every provider; changing, restarting, or resetting one provider MUST NOT alter any other provider.
- **FR-016**: An operator MUST be able to inspect a selected provider's active scenario, effective parameters, total scenario decisions, and current progression position without seeing quote request contents or sensitive data.
- **FR-017**: An operator MUST be able to restart the selected provider's scenario counters without changing its active scenario or parameters.
- **FR-018**: An operator MUST be able to restore the selected provider to `NORMAL` in one operation, clearing scenario parameters and returning all counters to their default state.
- **FR-019**: Each quote MUST use the selected provider's scenario state as it existed when that quote began; a later configuration change MUST apply only to subsequently begun quotes.
- **FR-020**: Concurrent quotes for a stateful scenario MUST each consume exactly one unique sequence position according to a stable order.
- **FR-021**: Scenario administration and inspection MUST be available only through an internal operational boundary intended for development, testing, review, and demonstrations, and MUST be unavailable from the public production-facing entry point.
- **FR-022**: Scenario administration responses and validation feedback MUST NOT disclose secrets, sensitive customer information, or customer request contents.
- **FR-023**: The supported scenarios, accepted parameters, valid ranges, reset behavior, and observable outcome for every scenario MUST be documented for operators.

### Key Entities *(include if feature involves data)*

- **Simulated Provider**: An independently controllable provider instance identified by a stable provider identifier and owning exactly one active scenario state.
- **Scenario Configuration**: The selected scenario and its validated effective parameters, associated with exactly one simulated provider.
- **Scenario Progression State**: Non-sensitive counters and current sequence position used to make stateful scenarios reproducible; it belongs to one provider and can be restarted independently.
- **Quote Outcome**: The provider-visible result of applying a scenario to a quote: valid quote, technical service unavailability, rate limitation, or valid no-driver business unavailability.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can configure, inspect, exercise, and reset all seven supported scenarios without restarting or replacing a provider.
- **SC-002**: In acceptance testing, 100% of valid scenario changes affect the next quote begun after activation, while 100% of already in-progress quotes retain their starting behavior.
- **SC-003**: Across at least 100 runs for each configured count, temporary-error scenarios fail and recover at exactly the configured quote positions with no extra or missing failures.
- **SC-004**: Across at least 100 reset-and-replay runs for each intermittent configuration, the observed success/failure sequence is identical to its first run, including tests with overlapping requests.
- **SC-005**: In isolation tests covering at least three providers, 100% of changes, counter restarts, and resets leave every non-selected provider's configuration and progression unchanged.
- **SC-006**: Across all invalid scenario names and parameter boundary cases, 100% are rejected without replacing or advancing the last valid scenario state, and every rejection identifies the invalid input.
- **SC-007**: A reviewer unfamiliar with the simulator can use the operator documentation to configure any supported scenario, verify it, and restore `NORMAL` in under two minutes without assistance.
- **SC-008**: In production-style exposure tests, 100% of public entry-point attempts to administer or inspect scenarios are unavailable, while approved internal operations remain usable.
- **SC-009**: Automated acceptance evidence covers all seven scenarios, parameter boundaries, invalid changes, deterministic replay, concurrent progression, inspection, counter restart, provider reset, provider isolation, and public-boundary exclusion.

## Assumptions

- The existing provider quote behavior from the Ranked Delivery Options feature supplies a valid quote in normal operation and distinguishes technical failure, rate limiting, and no-driver business unavailability.
- Scenario state is intentionally temporary and local to one provider instance; replacement or redeployment starts a fresh instance in `NORMAL`.
- Activating any scenario starts its progression counters at their default state. Restarting counters replays the current scenario, while restoring normal behavior selects `NORMAL` and clears all parameters.
- For delay verification, "approximately" means completion no earlier than the configured delay and within the normal scheduling tolerance documented for the test environment.
- The stable order for overlapping stateful requests is based on when each provider accepts the quote for scenario evaluation; operators need reproducible outcomes, not control over the ordering rule itself.
- Access restriction relies on the existing internal deployment boundary. Adding end-user authentication or a graphical administration interface is outside this feature.

## Dependencies and Scope

- **Dependency**: Ranked Delivery Options establishes the provider quote behavior this feature controls.
- **Enabled validation**: Resilient partial quotes, local demo operation, resilience observability, and repeatable resilience demonstrations can use these deterministic scenarios.
- **Out of scope**: Production provider integrations, provider onboarding, restaurant-user authentication, delivery dispatch, persistent scenario state, orchestrator timeout/retry/circuit-breaker policies, public production administration, and a graphical administration interface.
