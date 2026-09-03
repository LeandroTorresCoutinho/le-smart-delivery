# Tasks: Ranked Delivery Quotes

**Input**: Design documents from `specs/001-rank-delivery-quotes/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`, and constitution version 2.0.0

**Tests**: Automated tests are required by the feature definition of done and the project constitution. Within each story, write the listed tests first and verify they fail for the intended missing behavior before implementation.

**Organization**: Tasks are grouped by user story so each story can be implemented and verified as an incremental slice. Shared runtime, contract, observability, and test infrastructure appears only in Setup and Foundational phases.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel with other ready tasks because it owns different files and has no dependency on their incomplete changes.
- **[Story]**: Maps work to User Story 1, 2, or 3 from `spec.md`.
- Every checklist item names the exact file or directory it changes.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Reconcile the governed stack, initialize the Maven reactor, and establish build/container quality gates.

- [ ] T001 Reconcile the feature design with constitution 2.0.0 by replacing Spring Boot 3.5 compatibility work with Java 26 and Spring Boot 4.1.x decisions, updating the Resilience4j Boot 4 artifact, and normalizing the resilience dependency name in specs/001-rank-delivery-quotes/plan.md, specs/001-rank-delivery-quotes/research.md, and specs/001-rank-delivery-quotes/quickstart.md
- [ ] T002 Create the Java 26 Maven reactor, Maven Wrapper, centralized dependency/plugin management, compiler rules, Surefire/Failsafe separation, formatting, static-analysis, Maven Enforcer, and dependency-check gates in pom.xml, mvnw, mvnw.cmd, and .mvn/wrapper/
- [ ] T003 [P] Scaffold the Spring Boot 4.1.x orchestrator application and test source sets in delivery-orchestrator/pom.xml and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/DeliveryOrchestratorApplication.java
- [ ] T004 [P] Scaffold the Spring Boot 4.1.x simulator application and test source sets in provider-simulator/pom.xml and provider-simulator/src/main/java/com/lesmartdelivery/simulator/ProviderSimulatorApplication.java
- [ ] T005 Pin `io.github.resilience4j:resilience4j-spring-boot4:2.4.0` directly rather than relying on its incomplete BOM entry, and verify the Boot 4.1.x configuration/registry integration in pom.xml
- [ ] T006 [P] Add reproducible non-root Java 26 container builds with immutable-image metadata in delivery-orchestrator/Dockerfile, provider-simulator/Dockerfile, and .dockerignore
- [ ] T007 Define one internal network, one public orchestrator, and three internal instances of the reusable simulator with finite resources and health checks in compose.yaml
- [ ] T008 Add pull-request verification for the complete Maven reactor, integration tests, static analysis, dependency checks, and non-publishing image builds in .github/workflows/build.yml

**Checkpoint**: The repository has two buildable application modules and constitution-aligned build/container gates.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build the shared domain, provider boundary, configuration, correlation, problem, telemetry, and testing primitives required by every story.

**⚠️ CRITICAL**: No user-story implementation begins until this phase is complete and the governed-stack smoke gate passes.

- [ ] T009 Add context-startup smoke tests for JSON, Jakarta Validation, Actuator, Micrometer, RestClient, and Resilience4j Boot 4 registry/configuration initialization in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/integration/OrchestratorStackSmokeIT.java and provider-simulator/src/test/java/com/lesmartdelivery/simulator/integration/SimulatorStackSmokeIT.java
- [ ] T010 [P] Implement immutable cross-field validated ranking and provider configuration records, including unique provider IDs, 45/35/20 defaults, BRL currency, positive client timeouts, and finite bulkhead bounds in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/config/RankingProperties.java and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/config/ProviderProperties.java
- [ ] T011 [P] Implement immutable provider-neutral primitives and their invariants in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/CorrelationId.java, delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/GeoPoint.java, delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/Money.java, and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/ProviderId.java
- [ ] T012 [P] Define the provider-neutral outbound quote port and request type without simulator or HTTP dependencies in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/provider/DeliveryQuoteProvider.java and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/provider/ProviderQuoteRequest.java
- [ ] T013 [P] Implement validated simulator identity and deterministic NORMAL-profile configuration plus internal contract DTOs in provider-simulator/src/main/java/com/lesmartdelivery/simulator/config/SimulatorProperties.java and provider-simulator/src/main/java/com/lesmartdelivery/simulator/api/ProviderQuoteDtos.java
- [ ] T014 Build one provider-specific RestClient and one independently bounded ThreadPoolBulkhead per configured provider, with explicit connect/response timeouts and no implicit retry, in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/provider/ProviderClientFactory.java
- [ ] T015 [P] Implement request-scoped correlation parsing, safe UUIDv4 generation, response-header propagation, and context cleanup in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/observability/CorrelationContext.java and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/observability/CorrelationFilter.java
- [ ] T016 [P] Establish structured JSON event helpers and bounded-cardinality Micrometer instruments without coordinates, order values, bodies, secrets, or business IDs as tags in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/observability/DeliveryTelemetry.java
- [ ] T017 [P] Establish RFC 9457 problem types and deterministic field-violation ordering primitives in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/api/problem/ApiProblem.java and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/api/problem/FieldViolation.java
- [ ] T018 [P] Create reusable deterministic request, quote, provider, metric, and WireMock fixtures in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/support/DeliveryFixtures.java and delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/support/ProviderWireMockSupport.java
- [ ] T019 Run the two-module Java 26/Spring Boot 4.1.x smoke gate with ./mvnw verify and record the verified JDK, Boot, Resilience4j, plugin, context-startup, and container-build outcome in specs/001-rank-delivery-quotes/research.md

**Checkpoint**: Shared foundation is complete, bounded, observable, contract-ready, and verified on the governed stack.

---

## Phase 3: User Story 1 - Compare Ranked Delivery Options (Priority: P1) 🎯 MVP

**Goal**: A restaurant submits valid order and route details and receives one consistently formatted option per active provider, scored with the 45/35/20 policy and ordered deterministically by score.

**Independent Test**: With three deterministic NORMAL providers, submit one valid request and verify three provider-neutral options containing provider, BRL fee, whole-minute ETA, and two-decimal score in descending score order; repeat the same snapshot and obtain the same ordering and scores.

### Tests for User Story 1

- [ ] T020 [P] [US1] Write failing unit tests for lower-is-better min-max normalization, zero-range factors, one-provider scoring, decimal precision, and final HALF_UP rounding in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/unit/RankingEngineTest.java
- [ ] T021 [P] [US1] Write failing WireMock contract tests for request mapping, correlation propagation, currency/time conversion, configured provider identity, and provider-response validation in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/contract/ProviderQuoteAdapterContractTest.java
- [ ] T022 [P] [US1] Write failing internal HTTP contract tests for the deterministic NORMAL simulator response defined by contracts/provider-api.yaml in provider-simulator/src/test/java/com/lesmartdelivery/simulator/contract/ProviderQuoteControllerContractTest.java
- [ ] T023 [P] [US1] Write a failing three-provider integration test proving every active provider is called once and all quotes appear in the complete result in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/integration/CompleteQuoteAggregationIT.java
- [ ] T024 [P] [US1] Write a failing latch/barrier integration test proving provider fan-out begins concurrently without using elapsed-time-only assertions in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/integration/ConcurrentProviderFanOutIT.java

### Implementation for User Story 1

- [ ] T025 [P] [US1] Implement deterministic NORMAL quote generation and the internal POST /api/v1/quotes controller in provider-simulator/src/main/java/com/lesmartdelivery/simulator/application/NormalQuoteService.java and provider-simulator/src/main/java/com/lesmartdelivery/simulator/api/ProviderQuoteController.java
- [ ] T026 [P] [US1] Implement adapter-owned provider request/response DTOs, RestClient exchange, status mapping, currency verification, ETA conversion, and configured identity mapping in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/provider/SimulatorQuoteAdapter.java
- [ ] T027 [US1] Assemble one adapter for every active provider snapshot and reject duplicate or empty provider configuration at startup in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/config/ProviderAdapterConfiguration.java
- [ ] T028 [P] [US1] Implement provider-neutral quote, option, scoring snapshot, and complete-result models in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/ProviderQuote.java, delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/DeliveryOption.java, delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/ScoringSnapshot.java, and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/DeliveryOptionsResult.java
- [ ] T029 [US1] Implement BigDecimal min-max factor normalization, 45/35/20 weighted scoring, two-decimal HALF_UP public scores, and score-descending ordering in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/RankingEngine.java
- [ ] T030 [US1] Implement bounded concurrent active-provider collection and complete-result composition without all-or-nothing failure fallbacks in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/application/RequestDeliveryOptionsUseCase.java
- [ ] T031 [US1] Implement public request/response DTO mapping and POST /api/v1/delivery-options successful handling according to contracts/orchestrator-api.yaml in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/api/DeliveryOptionsDtos.java and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/api/DeliveryOptionsController.java
- [ ] T032 [US1] Emit provider completion, ranking completion, and complete-request structured events and bounded metrics from delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/application/RequestDeliveryOptionsUseCase.java and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/observability/DeliveryTelemetry.java
- [ ] T033 [US1] Configure three deterministic provider profiles, active-provider URLs, reliability values, independent timeouts/bulkheads, 45/35/20 weights, BRL, health endpoints, and internal-only simulator networking in delivery-orchestrator/src/main/resources/application.yaml, provider-simulator/src/main/resources/application.yaml, and compose.yaml

**Checkpoint**: User Story 1 independently returns stable, understandable, fully normalized ranked options from three active providers.

---

## Phase 4: User Story 2 - Act on a Clear Recommendation (Priority: P2)

**Goal**: Exactly one recommended provider is always the first ranked option, including equal-score ties and valid ranking-weight changes.

**Independent Test**: Feed controlled quotes that tie on score, then ETA, then fee, and verify the shorter ETA, lower fee, and case-sensitive ASCII provider ID tie chain selects one first option; change to another valid weight set and verify the recommendation follows the new first option without changing order input.

### Tests for User Story 2

- [ ] T034 [P] [US2] Write failing unit tests for sole recommendation, equal public-score ETA/fee/provider-ID tie breakers, numeric fee equality, and deterministic case-sensitive ASCII ordering in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/unit/RecommendationSelectorTest.java
- [ ] T035 [P] [US2] Write failing configuration tests for non-negative exact-100 weights, valid alternative weights, invalid totals, reliability bounds, and configuration snapshot consistency in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/unit/RankingPropertiesTest.java
- [ ] T036 [P] [US2] Write failing MockMvc/integration tests that enforce recommendedProvider equals options[0].provider for default, tie, one-provider, and reweighted scenarios in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/contract/RecommendationContractTest.java

### Implementation for User Story 2

- [ ] T037 [P] [US2] Implement the score/ETA/fee/provider-ID comparator and sole recommendation selection policy in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/RecommendationSelector.java
- [ ] T038 [US2] Integrate the public-score tie chain and recommendation selector into ranked result composition in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/domain/RankingEngine.java and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/application/RequestDeliveryOptionsUseCase.java
- [ ] T039 [US2] Enforce exact-sum ranking configuration startup validation and map any runtime-invalid snapshot to the controlled configuration problem in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/config/RankingProperties.java and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/api/problem/GlobalProblemHandler.java
- [ ] T040 [US2] Finalize response mapping so exactly one result-level recommendedProvider equals the first option and no per-option recommendation flags can diverge in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/api/DeliveryOptionsDtos.java

**Checkpoint**: User Story 2 independently proves that every valid ranking yields one clear, deterministic recommendation under default and changed weights.

---

## Phase 5: User Story 3 - Correct Invalid Delivery Details (Priority: P3)

**Goal**: Invalid requests return all detectable field-specific problems in deterministic order without contacting any provider, while valid or generated correlation identifiers are returned and propagated correctly.

**Independent Test**: Submit one parseable request with blank IDs, non-positive or excessive-scale value, and out-of-range coordinates; verify one 400 RFC 9457 response contains every field error and zero provider calls. Then prove a valid caller correlation value is preserved and an omitted value produces one UUIDv4 shared by response body/header and all provider requests.

### Tests for User Story 3

- [ ] T041 [P] [US3] Write failing boundary and aggregate-validation unit tests for blank IDs, currency minor-unit scale, zero/negative values, inclusive coordinate limits, missing nested fields, and deterministic pointer/code ordering in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/unit/DeliveryOptionsRequestValidatorTest.java
- [ ] T042 [P] [US3] Write failing MockMvc contract tests for 400 application/problem+json, complete errors arrays, malformed JSON, safe details, and the controlled 500 configuration problem in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/contract/ValidationProblemContractTest.java
- [ ] T043 [P] [US3] Write a failing WireMock integration test proving no configured provider receives a request for any validation failure in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/integration/ValidationShortCircuitIT.java
- [ ] T044 [P] [US3] Write failing contract/integration tests for preserved, omitted, malformed, and duplicate X-Correlation-Id values across response headers, bodies, provider calls, logs, and cleanup in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/contract/CorrelationIdContractTest.java

### Implementation for User Story 3

- [ ] T045 [P] [US3] Add Jakarta structural constraints matching the public contract to delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/api/DeliveryOptionsDtos.java
- [ ] T046 [US3] Implement aggregate semantic validation for opaque IDs, order-value positivity/scale, coordinates, and nested fields in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/application/DeliveryOptionsRequestValidator.java
- [ ] T047 [US3] Map binding, parsing, header, semantic, and configuration failures to safe deterministically ordered RFC 9457 problems in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/api/problem/GlobalProblemHandler.java
- [ ] T048 [US3] Complete valid-header preservation, invalid-header replacement for problem correlation, UUIDv4 generation, body/header emission, outbound provider propagation, structured-context propagation, and cleanup in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/observability/CorrelationFilter.java and delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/provider/SimulatorQuoteAdapter.java
- [ ] T049 [US3] Place the aggregate validation gate before provider snapshot resolution and fan-out so every invalid request short-circuits in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/application/RequestDeliveryOptionsUseCase.java
- [ ] T050 [US3] Emit validation rejection events and bounded field/code metrics without rejected values or high-cardinality identifiers in delivery-orchestrator/src/main/java/com/lesmartdelivery/orchestrator/observability/DeliveryTelemetry.java

**Checkpoint**: All three stories are independently testable, and invalid requests cannot consume provider capacity.

---

## Phase 6: Polish & Cross-Cutting Verification

**Purpose**: Close contract drift, observability safety, packaged validation, performance, documentation, and full quality gates.

- [ ] T051 [P] Add automated OpenAPI schema/reference validation and response-contract drift checks for specs/001-rank-delivery-quotes/contracts/orchestrator-api.yaml and specs/001-rank-delivery-quotes/contracts/provider-api.yaml in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/contract/OpenApiContractConsistencyTest.java
- [ ] T052 [P] Add JSON-log safety and bounded-metric-tag tests covering all three stories in delivery-orchestrator/src/test/java/com/lesmartdelivery/orchestrator/integration/ObservabilitySafetyIT.java
- [ ] T053 [P] Implement the fixed 20-warm-up, 200-request, concurrency-10 acceptance workload with correctness and p50/p95/p99 reporting in scripts/generate-load.sh
- [ ] T054 [P] Add a packaged Compose smoke script that waits for health, checks three ranked options, recommendation, determinism, generated/preserved correlation, aggregate validation, and zero provider-call evidence in scripts/smoke-ranked-quotes.sh
- [ ] T055 Update repository startup, ranked-quote examples, configuration keys, internal/public exposure, verification commands, the 002-provider-failure-isolation release dependency, and the 003-provider-scenario-control exclusion in README.md
- [ ] T056 Execute every runnable scenario in specs/001-rank-delivery-quotes/quickstart.md and record environment, command, expected/actual outcome, and any approved deviation in specs/001-rank-delivery-quotes/checklists/quickstart-validation.md
- [ ] T057 Run the complete ./mvnw clean verify quality gate and resolve compilation, unit, contract, integration, formatting, static-analysis, and dependency-check failures in pom.xml and the failing module paths reported by Maven
- [ ] T058 Build both container images and run docker compose plus scripts/generate-load.sh, recording immutable image identifiers, health, correctness, latency, and the production-release block on provider resilience in specs/001-rank-delivery-quotes/checklists/release-evidence.md

**Checkpoint**: Feature 001 is locally demonstrable, contract-verified, and ready to integrate with provider-failure isolation, but is not production-release eligible until that resilience feature passes its constitution gates.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: Starts immediately. T003 and T004 can run together after T002; T005 follows dependency management in T002; T006 can proceed after both module paths exist; T007 and T008 consume the completed setup.
- **Phase 2 — Foundational**: Depends on Phase 1. T010–T013 and T015–T018 can run in parallel; T014 depends on T010 and T012; T019 depends on all foundational work and blocks user-story implementation if it fails.
- **Phase 3 — User Story 1**: Depends on Phase 2. Tests T020–T024 are written first in parallel. T025, T026, and T028 can then proceed in parallel; T027 depends on T026; T029 depends on T028; T030 depends on T027 and T029; T031–T033 complete the slice.
- **Phase 4 — User Story 2**: Depends on the ranked option model and scoring flow delivered by User Story 1. Tests T034–T036 are parallel and precede T037–T040.
- **Phase 5 — User Story 3**: Depends only on Phase 2 for test-first validator/problem work and can be developed alongside User Story 1 in separate files; final integration tasks T048–T050 rebase onto the completed request use case and adapter.
- **Phase 6 — Polish**: Depends on all selected user stories. T051–T054 can run in parallel before sequential end-to-end evidence T056–T058.

### User Story Dependency Graph

```text
Setup → Foundation ──→ US1: Ranked options ──→ US2: Recommendation and ties ──┐
                    └→ US3: Validation and correlation ────────────────────────┤
                                                                                └→ Polish and release evidence
```

### User Story Independence

- **US1 (P1)**: Independently verified with three deterministic valid providers and repeated equal snapshots; it does not require validation-problem or resilience behavior.
- **US2 (P2)**: Uses US1's option/scoring model but is independently verified through controlled recommendation and weight fixtures without requiring US3.
- **US3 (P3)**: Independently verified with fake/WireMock providers and invalid requests; it does not require recommendation tie cases and proves providers are never called.

### Parallel Opportunities

- Scaffold the two modules concurrently in T003 and T004.
- Implement configuration, domain primitives, outbound port, simulator types, correlation, telemetry, problems, and fixtures concurrently in T010–T013 and T015–T018.
- Write the five US1 tests concurrently in T020–T024, then implement simulator, adapter, and domain models concurrently in T025, T026, and T028.
- Write all US2 tests concurrently in T034–T036.
- Write all US3 tests concurrently in T041–T044 while US1 implementation proceeds in non-overlapping files.
- Run OpenAPI, observability, load-script, and smoke-script polish work concurrently in T051–T054.

## Parallel Execution Examples

### User Story 1

```text
Parallel test batch:
- T020 RankingEngineTest.java
- T021 ProviderQuoteAdapterContractTest.java
- T022 ProviderQuoteControllerContractTest.java
- T023 CompleteQuoteAggregationIT.java
- T024 ConcurrentProviderFanOutIT.java

Parallel implementation batch after failing tests exist:
- T025 simulator NORMAL quote service/controller
- T026 orchestrator simulator adapter
- T028 provider-neutral quote/result models
```

### User Story 2

```text
Parallel test batch:
- T034 RecommendationSelectorTest.java
- T035 RankingPropertiesTest.java
- T036 RecommendationContractTest.java

Then T037 → T038, with T039 and T040 following their affected test failures.
```

### User Story 3

```text
Parallel test batch:
- T041 DeliveryOptionsRequestValidatorTest.java
- T042 ValidationProblemContractTest.java
- T043 ValidationShortCircuitIT.java
- T044 CorrelationIdContractTest.java

Parallel first implementation step:
- T045 structural constraints
- T046 semantic validator after its unit fixtures are stable
```

## Implementation Strategy

### MVP First — User Story 1

1. Complete Setup and the constitution-aligned stack reconciliation.
2. Complete Foundation and stop immediately if T019 fails.
3. Write and observe failures for T020–T024.
4. Implement T025–T033.
5. Stop and validate the three-provider ranked-options slice independently.

This MVP demonstrates the core customer comparison value. The complete feature contract requires US2 for formal recommendation/tie guarantees and US3 for customer-correctable validation behavior.

### Incremental Delivery

1. **Foundation**: Two verified applications, bounded provider boundaries, safe correlation, telemetry, and reusable test fixtures.
2. **US1**: Complete normalized ranked quote comparison from three providers.
3. **US2**: One deterministic recommendation under ties and changed weights.
4. **US3**: Aggregate field validation, correlation guarantees, and zero provider work for invalid input.
5. **Polish**: Contract drift, safe observability, packaged smoke, repeatable performance evidence, and release-boundary documentation.

### Release Boundary

Feature 001 must not implement fake all-or-nothing failure handling and must not be exposed as a production-ready endpoint by itself. Provider timeouts, selective retry, circuit breaker, fallback, partial results, and controlled total failure remain the provider-failure-isolation feature's release obligation under constitution principles I, II, III, and V.

## Notes

- `[P]` marks tasks that can be executed concurrently only after their stated prerequisites are ready.
- User-story labels provide requirement traceability; Setup, Foundational, and Polish tasks intentionally have no story label.
- Tests precede implementation because the specification and constitution explicitly require automated behavioral evidence.
- Provider-specific DTOs remain inside adapter and simulator API packages; they must not be shared with the ranking domain.
- Correlation, restaurant, and order identifiers belong in structured logs, never in metric tags.
- Stop at each checkpoint and preserve independently passing story tests before starting the next dependent slice.
