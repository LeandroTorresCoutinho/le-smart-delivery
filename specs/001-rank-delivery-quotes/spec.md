# Feature Specification: Ranked Delivery Quotes

**Feature Branch**: `main` (no branch-creation hook configured)

**Created**: 2026-09-01

**Status**: Draft

**Input**: User description: "Allow a restaurant to submit an order's delivery details and receive consistently formatted, deterministically ranked quotes from every configured delivery provider, with one clearly recommended option."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Compare Ranked Delivery Options (Priority: P1)

As a restaurant, I want to submit an order's pickup and destination details and receive all available delivery quotes in a consistent ranked list so that I can quickly compare delivery time, price, and reliability.

**Why this priority**: Comparing valid quotes is the core customer value. Without it, the restaurant cannot make an informed delivery choice.

**Independent Test**: Submit a valid request while three configured providers each return a valid quote. Verify that three understandable options are returned in descending score order and that each option contains the provider, fee, estimated delivery time, and calculated score.

**Acceptance Scenarios**:

1. **Given** a valid request and three configured providers that each return a valid quote, **When** the restaurant requests delivery options, **Then** all three quotes are returned in one consistent format and ordered from highest to lowest score.
2. **Given** valid quotes with different combinations of estimated delivery time, fee, and provider reliability, **When** the options are scored, **Then** each score reflects the active weights of 45% for estimated delivery time, 35% for price, and 20% for reliability.
3. **Given** the same request, provider quotes, reliability values, and ranking configuration, **When** ranking is repeated, **Then** the scores and option order are identical.

---

### User Story 2 - Act on a Clear Recommendation (Priority: P2)

As a restaurant, I want the best-ranked option identified clearly so that I can recognize the preferred delivery provider without interpreting the scoring details myself.

**Why this priority**: A recommendation turns a comparison into an actionable decision and reduces the restaurant's decision effort.

**Independent Test**: Produce at least two valid options, verify that exactly one is identified as recommended, and confirm that it is the first option after deterministic ranking.

**Acceptance Scenarios**:

1. **Given** two or more valid provider quotes with different scores, **When** delivery options are returned, **Then** exactly one option is recommended and it is the highest-scoring option.
2. **Given** two provider quotes with equal calculated scores, **When** the options are ranked, **Then** the option with the shorter estimated delivery time ranks first; if still tied, the lower delivery fee ranks first; if still tied, the provider identifier in ascending lexical order ranks first.
3. **Given** the ranking weights are changed to another valid configuration, **When** the same quotes are ranked, **Then** the scores, order, and recommendation reflect the new configuration without changing the submitted order details.

---

### User Story 3 - Correct Invalid Delivery Details (Priority: P3)

As a restaurant, I want all invalid or missing delivery details identified clearly so that I can correct the request without causing unnecessary quote attempts.

**Why this priority**: Clear validation prevents confusing results and avoids work that cannot produce a useful quote.

**Independent Test**: Submit a request containing multiple missing or invalid fields. Verify that every detected field problem is returned in understandable language and that no configured provider is contacted.

**Acceptance Scenarios**:

1. **Given** a request with one or more missing required fields, **When** delivery options are requested, **Then** the response identifies every detected missing field and no provider quote is requested.
2. **Given** a request containing a non-positive order value or coordinates outside their valid geographic ranges, **When** delivery options are requested, **Then** the response identifies each invalid value and no provider quote is requested.
3. **Given** a valid request without a caller-provided correlation identifier, **When** delivery options are requested, **Then** a new correlation identifier is included in the response.
4. **Given** a valid request with a caller-provided correlation identifier, **When** delivery options are requested, **Then** the same identifier is included in the response.

### Edge Cases

- If only one provider is configured and returns a valid quote, that option is returned and is the sole recommendation.
- If no provider is configured, or if any configured provider fails to produce a valid quote, behavior belongs to the separate resilience and partial-results feature and is not defined by this successful-flow specification.
- Latitude values at -90 and 90 and longitude values at -180 and 180 are valid; values outside those inclusive ranges are rejected before providers are contacted.
- Zero or negative order values are rejected before providers are contacted.
- Ranking weights must each be non-negative and must total 100%; an invalid active configuration prevents ranking and produces a controlled, understandable error rather than a misleading recommendation.
- Equal scores are resolved by shorter estimated delivery time, then lower fee, then provider identifier in ascending lexical order, ensuring one stable recommendation.
- Delivery fees with more precision than the platform currency supports are represented according to that currency's standard minor units, without changing the quoted monetary meaning.
- If a provider reports delivery time in a different unit, the restaurant still sees all estimates in one consistent unit.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The platform MUST accept a delivery-options request containing a restaurant identifier, order identifier, positive order value, pickup latitude and longitude, destination latitude and longitude, and an optional correlation identifier.
- **FR-002**: The platform MUST validate every required request field and return field-specific information for all detected problems in a single response.
- **FR-003**: The platform MUST NOT request quotes from any provider when request validation fails.
- **FR-004**: For a valid request, the platform MUST consider every provider configured as active for delivery quotes at the time of the request.
- **FR-005**: The platform MUST represent each valid provider quote as a delivery option containing provider identifier, delivery fee, estimated delivery time, and calculated score.
- **FR-006**: The platform MUST express delivery fees according to the platform currency's standard minor-unit precision and MUST express estimated delivery time in a single consistent unit across all options.
- **FR-007**: The platform MUST calculate each option's score from independently configurable estimated-time, price, and provider-reliability factors.
- **FR-008**: The initial active ranking configuration MUST weight estimated delivery time at 45%, price at 35%, and provider reliability at 20%.
- **FR-009**: A valid ranking configuration MUST use non-negative factor weights totaling 100%, and changing valid weights MUST NOT require changes to submitted order information or the core comparison behavior.
- **FR-010**: The platform MUST rank valid options from highest to lowest calculated score.
- **FR-011**: The platform MUST rank equal-scoring options by shorter estimated delivery time, then lower delivery fee, then provider identifier in ascending lexical order.
- **FR-012**: For one or more valid options, the platform MUST identify exactly one recommended provider, and it MUST be the first provider in the ranked list.
- **FR-013**: The platform MUST produce identical scores, ordering, and recommendation when the request, valid provider quotes, reliability values, and ranking configuration are unchanged.
- **FR-014**: The platform MUST preserve a caller-provided correlation identifier in the response or generate and return one when the caller omits it.
- **FR-015**: A successful response under this feature MUST state that the result is complete and that there are no unavailable providers.

### Scope Boundaries

This feature includes request validation, successful quote collection from every configured provider, provider-neutral option representation, configurable scoring, deterministic ordering, recommendation, monetary and delivery-time consistency, and correlation identifier handling.

It excludes provider timeouts and retries, circuit breakers, partial results, total-provider-failure handling, provider scenario administration, delivery creation or dispatch, persistent order management, restaurant-specific ranking policies, authentication, tenant isolation, production deployment, and operational dashboards. Those capabilities require separate specifications. The resilience and partial-results feature is a dependency for constitution-compliant behavior when one or more providers are unavailable; it does not alter this feature's successful-flow requirements.

### Requirement Acceptance Mapping

- **FR-001–FR-003** are accepted through User Story 3 scenarios 1 and 2 and the coordinate and order-value edge cases.
- **FR-004–FR-010** are accepted through User Story 1 scenarios 1 and 2 and User Story 2 scenario 3.
- **FR-011–FR-013** are accepted through User Story 1 scenario 3 and User Story 2 scenarios 1 and 2.
- **FR-014** is accepted through User Story 3 scenarios 3 and 4.
- **FR-015** is accepted through User Story 1 scenario 1 by verifying the successful response is marked complete and lists no unavailable providers.

### Key Entities

- **Delivery-options request**: The restaurant's quote-comparison request, identified by restaurant and order, with order value, pickup coordinates, destination coordinates, and optional correlation identifier.
- **Provider quote**: A configured provider's successful offer for the requested delivery, including provider identity, delivery fee, and estimated delivery time before comparison.
- **Delivery option**: The provider-neutral representation shown to the restaurant, containing provider identifier, normalized fee, consistent delivery-time estimate, and calculated score.
- **Ranking configuration**: The active non-negative weights for estimated delivery time, price, and provider reliability; the weights total 100%.
- **Provider reliability value**: The current comparable reliability measure associated with a provider and used as one ranking input.
- **Delivery-options result**: The correlation identifier, ranked delivery options, recommended provider, completeness indicator, and unavailable-provider list returned to the restaurant.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of defined validation cases, all detectable invalid fields are identified in one understandable response and no provider is contacted.
- **SC-002**: In 100% of defined successful three-provider scenarios, all three quotes are represented with provider, fee, estimated time, and score, and are ordered according to the active ranking configuration.
- **SC-003**: The recommended provider matches the first ranked option in 100% of successful scenarios, including all documented tie cases.
- **SC-004**: Repeating an evaluation with unchanged request information, quotes, reliability values, and ranking weights produces the same scores and ranking in 100% of test repetitions.
- **SC-005**: At least 95% of successful three-provider requests present the complete ranked result to the restaurant within 2 seconds under the agreed acceptance workload.
- **SC-006**: At least 90% of representative restaurant users or business reviewers can identify the recommended provider and explain the fee and estimated time of every option without assistance during acceptance testing.
- **SC-007**: In 100% of valid ranking-configuration test cases, changing the weights changes scores and ordering only as dictated by the new weights while preserving the submitted order details.
- **SC-008**: Every successful response includes a correlation identifier, and 100% of caller-provided identifiers are returned unchanged.

## Assumptions

- The successful flow has at least one active configured provider, and every active provider returns a valid quote. Provider unavailability is handled by the separate resilience and partial-results feature.
- The initial MVP uses three configured delivery providers, while the rules apply to any positive number of active configured providers.
- Provider reliability values are available, current, and expressed on a comparable scale before ranking begins; how those values are produced is outside this feature.
- The platform uses one configured currency for the successful-flow MVP, and monetary values follow that currency's standard minor units.
- Order identifiers are unique within the restaurant's business context; this feature does not persist or dispatch the order.
- Restaurant authentication and tenant isolation are outside the MVP, as required by the project constitution.
- The separate resilience feature will define partial results, unavailable-provider reporting, total failure, retries, timeouts, and circuit behavior required by the constitution.
- The agreed acceptance workload and test environment will be fixed before measuring the 2-second response target so results are repeatable.
