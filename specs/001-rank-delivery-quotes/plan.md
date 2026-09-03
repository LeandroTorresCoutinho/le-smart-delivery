# Implementation Plan: Ranked Delivery Quotes

**Branch**: `001-rank-delivery-quotes` | **Date**: 2026-09-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-rank-delivery-quotes/spec.md`

## Summary

Build the first successful delivery-options flow as two Maven-built Spring Boot applications: a public delivery orchestrator and one reusable provider simulator run as three configured instances. The orchestrator validates requests before any outbound work, calls each active provider concurrently through provider-specific adapters and bounded execution budgets, converts responses to provider-neutral domain quotes, applies configuration-driven decimal scoring, orders ties deterministically, and returns exactly one recommendation. The design is stateless, observable, contract-first, and leaves failure outcomes compatible with the separately specified provider-resilience feature.

## Technical Context

**Language/Version**: Java 26 without preview features; Spring Boot 4.1.1. This constitution-mandated pairing is supported by the selected Spring Boot generation and remains subject to the governed-stack smoke gate defined in [research.md](research.md).

**Primary Dependencies**: Spring Web MVC and `RestClient`, Jakarta Validation, Spring Boot Actuator, Micrometer Prometheus, `io.github.resilience4j:resilience4j-spring-boot4:2.4.0` and AOP, Jackson, and Apache Maven 3.9.x through the Maven Wrapper. Maven Enforcer, static analysis, formatting, and dependency-vulnerability plugins participate in `verify`.

**Storage**: None. Requests, quotes, and ranking results are request-scoped; ranking weights, provider metadata, provider endpoints, reliability values, timeouts, and bounded execution settings are external configuration.

**Testing**: JUnit 5, AssertJ, Mockito, Spring Boot Test with MockMvc, WireMock for provider boundaries, `SimpleMeterRegistry` for metric assertions, Maven Surefire/Failsafe, Docker Compose smoke tests, and a repeatable 200-request acceptance workload at concurrency 10.

**Target Platform**: Linux containers for local Docker Compose and later GKE Autopilot deployment; development supported on systems with JDK 26, Maven Wrapper, and Docker Compose.

**Project Type**: Multi-module web service with two independently deployable applications: `delivery-orchestrator` and `provider-simulator`.

**Performance Goals**: All active provider calls begin concurrently; under the documented acceptance workload, at least 95% of successful three-provider requests return a complete ranked response within 2 seconds, with zero incorrect rankings or recommendations.

**Constraints**: No persistence, UI, authentication, real provider integration, dispatch, or provider failure behavior in this feature. Provider contracts cannot enter the domain model. Each provider has an independent HTTP client and bounded bulkhead. Money and scores use decimal arithmetic; weights are validated and externalized. Only the orchestrator is externally exposed. Logs must be structured and omit coordinates, order value, request bodies, and secrets. This slice cannot be considered production-release complete until `003-provider-failure-isolation` satisfies the constitution's partial-success and resilience requirements.

**Scale/Scope**: One orchestrator, one simulator artifact deployed as three providers, one public quote-comparison operation, one internal quote operation, three ranking factors, no database, and an initial acceptance workload of 20 warm-up requests followed by 200 measured requests at concurrency 10.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-design gate

| Principle or constraint | Result | Design evidence and obligation                                                                                                                                                                                                                                                           |
|---|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| I. Useful Results Through Partial Success | PASS for this scoped design; release dependency recorded | The response contract already carries `partialResult` and `unavailableProviders`, and this feature introduces no all-or-nothing failure semantics. Feature `003-provider-failure-isolation` remains mandatory before production release to implement partial and total-failure behavior. |
| II. Provider Isolation and Explicit Resilience | PASS for this scoped design; release dependency recorded | Each provider gets a separate adapter, `RestClient`, connection/response timeout configuration, bounded bulkhead, and telemetry identity. Retry, circuit-breaker, and fallback behavior is added and proven by feature 002 before release.                                               |
| III. Safe Retries and Idempotent Dispatch | PASS | This feature implements quote requests only, defines no retry, and adds no delivery creation or dispatch. No implicit HTTP retries are permitted.                                                                                                                                        |
| IV. Domain Independence and Bounded Concurrency | PASS | Provider DTOs remain inside adapters; scoring consumes provider-neutral quotes; weights are validated configuration; each provider call uses independently bounded execution.                                                                                                            |
| V. Observable Behavior Proven by Tests | PASS for the successful-flow slice | Unit, contract, integration, concurrency, configuration, log, metric, and end-to-end checks cover validation, normalization, scoring, ordering, recommendation, and correlation. The full resilience matrix remains feature 002's release obligation.                                    |
| Mandated stack and MVP boundary | PASS | Java 26, Spring Boot 4.1.x, Maven, Resilience4j, Actuator, Micrometer, Compose, reusable simulator, and three providers are retained. No deferred database, messaging, UI, authentication, or real integrations are introduced.                                                              |
| Public/internal exposure | PASS | The public contract belongs only to the orchestrator. Provider quote and future administration interfaces remain internal.                                                                                                                                                               |
| Development quality gates | PASS by plan | Root `verify` covers all modules, unit/integration checks, static analysis, formatting, and dependency checks; images must build in PR validation.                                                                                                                                       |

No constitution violation is accepted. The Java 26/Spring Boot 4.1.x stack is validated by a mandatory compile, context-startup, integration, and container smoke gate before feature behavior is considered complete.

## Project Structure

### Documentation (this feature)

```text
specs/001-rank-delivery-quotes/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── orchestrator-api.yaml
│   └── provider-api.yaml
└── tasks.md                 # Created later by $speckit-tasks
```

### Source Code (repository root)

```text
pom.xml
mvnw
mvnw.cmd
.mvn/wrapper/

delivery-orchestrator/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/java/com/lesmartdelivery/orchestrator/
    │   ├── api/             # public DTOs, controller, problem mapping
    │   ├── application/     # validation orchestration and quote use case
    │   ├── domain/          # provider-neutral values, scoring, ranking
    │   ├── provider/        # outbound port and provider-specific adapters
    │   ├── config/          # validated provider/ranking/bulkhead properties
    │   └── observability/   # correlation, structured events, bounded metrics
    ├── main/resources/
    │   └── application.yaml
    └── test/java/com/lesmartdelivery/orchestrator/
        ├── unit/
        ├── contract/
        └── integration/

provider-simulator/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/java/com/lesmartdelivery/simulator/
    │   ├── api/             # internal quote contract
    │   ├── application/     # deterministic NORMAL quote behavior
    │   └── config/          # instance provider ID and quote profile
    ├── main/resources/
    │   └── application.yaml
    └── test/java/com/lesmartdelivery/simulator/
        ├── unit/
        └── contract/

scripts/
└── generate-load.sh

compose.yaml
```

**Structure Decision**: Use the README's two-application Maven reactor. The orchestrator owns the provider-neutral domain and the external-provider adapters; the simulator is intentionally not shared as a contract or domain dependency because it represents an external boundary. One simulator image is configured three times in Compose. Root lifecycle commands compile, verify, package, and build both deployables consistently.

## Phase 0: Research Decisions

All technical unknowns are resolved in [research.md](research.md). The decisive choices are:

1. Use Java 26 with Spring Boot 4.1.1 under a mandatory governed-stack smoke gate.
2. Use two Maven modules and no shared provider-contract module.
3. Use one synchronous `RestClient` and one bounded `ThreadPoolBulkhead` per provider.
4. Use validated immutable configuration for provider metadata, execution bounds, currency, reliability, and ranking weights.
5. Use provider-neutral domain values, exact decimal money/scoring, min-max factor normalization, and the specification's deterministic tie chain.
6. Use OpenAPI 3.1 contracts and RFC 9457 problem details with deterministic field errors.
7. Use focused units, MockMvc contracts, WireMock integrations, Compose smoke checks, structured-log/metric assertions, and a fixed acceptance workload.

## Phase 1: Design Outputs

- [data-model.md](data-model.md) defines request-scoped entities, value rules, relationships, scoring calculations, and lifecycle states.
- [orchestrator-api.yaml](contracts/orchestrator-api.yaml) defines the public delivery-options and validation-problem contract.
- [provider-api.yaml](contracts/provider-api.yaml) defines the internal provider quote boundary used by each adapter.
- [quickstart.md](quickstart.md) defines reproducible build, contract, validation, determinism, correlation, configuration, and performance checks.

### Post-design constitution re-check

| Gate | Result after design | Evidence |
|---|---|---|
| Useful result shape remains compatible with later partial success | PASS | Public result includes `partialResult` and `unavailableProviders`; feature 001 constrains only the complete-success case. |
| Provider isolation is preserved | PASS | Data model separates provider DTOs from domain quotes; internal contract is adapter-owned; plan allocates separate clients, timeouts, bulkheads, and bounded telemetry per provider. |
| Retries and dispatch remain safe | PASS | Neither contract defines dispatch; no retry behavior is introduced. |
| Domain independence and bounded concurrency are explicit | PASS | Ranking has no HTTP types, provider IDs are trusted configuration, and executor/bulkhead values are finite validated settings. |
| Observable behavior is testable | PASS | Quickstart and test plan validate provider fan-out, no calls on invalid input, exact ordering, recommendation, correlation, JSON logs, bounded metrics, and end-to-end latency. |
| MVP scope and governed stack are preserved | PASS | No prohibited storage or product capability appears. Java 26 and Spring Boot 4.1.x match constitution 2.0.0 and are covered by an explicit smoke gate. |

Phase 1 introduces no new constitution violations. Production release remains blocked on feature 002's resilience implementation and test evidence, as required by the constitution rather than by this feature's successful-flow scope.
