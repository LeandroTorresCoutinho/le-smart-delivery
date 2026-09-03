# Implementation Plan: Provider Failure Isolation

**Branch**: `002-provider-failure-isolation` | **Date**: 2026-09-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-provider-failure-isolation/spec.md`

## Summary

Extend the delivery orchestrator so every configured provider runs through an independently bounded resilience pipeline and the restaurant receives a useful ranked response whenever at least one valid quote arrives before the overall deadline. The design snapshots provider and ranking configuration per request, fans out provider work concurrently, applies an explicit provider-scoped admission → circuit → retry → per-attempt timeout/HTTP sequence, maps every terminal condition into a provider-neutral outcome, and finalizes exactly one complete `200`, partial `200`, or controlled RFC 9457 `503`. Retry and circuit predicates are intentionally separate, circuit state is process-local and isolated by provider, business no-driver outcomes never count as technical failures, and late completions cannot mutate a finalized result.

## Technical Context

**Language/Version**: Java 26 without preview features; Spring Boot 4.1.x, as mandated by constitution 2.0.0

**Primary Dependencies**: Spring Web MVC and `RestClient`, Jakarta Validation, Spring Boot Actuator, Micrometer Prometheus, Jackson, Resilience4j 2.4.0 core modules plus `resilience4j-spring-boot4` pinned explicitly, and Apache Maven 3.9.x through the Maven Wrapper. Resilience4j 2.4.0 adds Spring Boot 4 support, but its BOM omits the Boot 4 starter, so dependency resolution and metrics binding are an explicit first implementation gate.

**Storage**: None. Requests, attempt/outcome records, aggregation state, and circuit state are process-local; circuit state is not persisted across restarts.

**Testing**: JUnit 5, AssertJ, Mockito, Spring Boot Test with MockMvc, WireMock per provider boundary, `SimpleMeterRegistry`, deterministic clock/random fixtures, latch/barrier concurrency tests, Maven Surefire/Failsafe, Docker Compose smoke tests, and the existing 20-warm-up/200-request workload at concurrency 10 across the supported failure matrix

**Target Platform**: Linux containers for local Docker Compose and later Kubernetes/GKE Autopilot; development uses JDK 26, the Maven Wrapper, Docker, `curl`, and `jq`

**Project Type**: Maven reactor with two independently deployable web services: `delivery-orchestrator` and one reusable `provider-simulator` artifact deployed as three configured provider instances

**Performance Goals**: Every provider fan-out begins concurrently after validation; under the documented acceptance workload, 100% of results have correct complete/partial/total classification, all work stays within configured provider bounds, and at least 95% of requests with one or more valid providers finalize within the configured 2-second overall deadline

**Constraints**: Maximum three total attempts per provider request; increasing backoff with injected jitter; explicit HTTP connection and response timeouts in addition to an attempt time limit; no retry when the delay plus next bounded attempt cannot fit before the overall deadline; one provider's failures, queues, retry budget, circuit, and metrics cannot affect another provider; no cached fallback, dispatch, persistence, real integration, UI, authentication, or public simulator administration; restaurant responses expose provider IDs but not failure internals

**Scale/Scope**: One public delivery-options operation, one internal provider quote operation, three configured MVP providers, one terminal participation outcome per provider per request, at most nine outbound attempts per delivery-options request under the default three-provider/three-attempt policy, and no database, cache, or broker

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-design gate

| Principle or constraint | Result | Design evidence and obligation |
|---|---|---|
| I. Useful Results Through Partial Success | PASS | Aggregation returns ranked quotes whenever at least one valid quote exists, marks mixed results partial, reports every unavailable provider, and maps zero valid quotes to a controlled `503`. |
| II. Provider Isolation and Explicit Resilience | PASS | Every provider owns a distinct adapter, HTTP client configuration, admission bound, retry policy, circuit instance, fallback mapping, and telemetry identity. The functional sequence and its negative cases are test obligations. |
| III. Safe Retries and Idempotent Dispatch | PASS | Only the read-like quote operation is retried. Retry classification is explicit, attempts are capped at three, backoff is exponential with jitter, and no delivery creation or dispatch is introduced. |
| IV. Domain Independence and Bounded Concurrency | PASS | Provider HTTP payloads terminate at adapters; provider-neutral outcomes feed aggregation and ranking; concurrency, queues, attempts, timeouts, and deadlines are finite validated configuration. |
| V. Observable Behavior Proven by Tests | PASS | The plan covers the complete mandatory success/failure/recovery matrix through unit, contract, WireMock, concurrency, Compose, log, metric, and load evidence. |
| Governed stack and MVP boundary | PASS with dependency-resolution gate | Java 26, Spring Boot 4.1.x, Maven, Resilience4j, Actuator, Micrometer, Compose, the orchestrator, and the reusable simulator remain in scope. Resilience4j's Boot 4 starter is pinned directly and proven before implementation continues. |
| Public/internal exposure | PASS | Only the orchestrator contract is public. Provider quote and scenario-control endpoints remain internal. |
| Development quality gates | PASS by plan | Root `verify` must cover both modules, static analysis, dependency checks, contract/integration tests, and container builds without publishing from pull requests. |

No constitution violation is accepted. Constitution 2.0.0 supersedes stale Spring Boot 3 references in the README and features 001/003. Feature 001's design must be reconciled and implemented before full orchestration integration; feature 003's plan must be rerun for Boot 4 before Compose scenario validation. WireMock fixtures keep feature 002 independently testable while those dependencies are completed.

## Project Structure

### Documentation (this feature)

```text
specs/002-provider-failure-isolation/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── orchestrator-api.yaml
│   └── provider-api.yaml
├── checklists/
│   └── requirements.md
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
    │   ├── api/             # complete/partial result and controlled problem mapping
    │   ├── application/     # deadline-aware fan-out, aggregation, finalization
    │   ├── domain/          # provider-neutral outcomes, scoring, result invariants
    │   ├── provider/        # outbound port, adapters, explicit resilience pipeline
    │   ├── config/          # immutable validated per-provider policies
    │   └── observability/   # safe events and bounded provider/policy metrics
    ├── main/resources/
    │   └── application.yaml
    └── test/java/com/lesmartdelivery/orchestrator/
        ├── unit/
        ├── contract/
        └── integration/

provider-simulator/          # feature 003 owns deterministic scenario behavior
├── pom.xml
├── Dockerfile
└── src/

scripts/
├── generate-load.sh
└── set-provider-scenario.sh

compose.yaml
```

**Structure Decision**: Retain feature 001's planned two-application Maven reactor and extend the orchestrator only. The simulator remains an external boundary and is not shared as a Java contract or domain module. Although the repository currently contains only specifications and the README, these paths are the agreed implementation target; feature 001 creates the base skeleton and successful ranking flow, feature 002 adds orchestrator resilience, and feature 003 implements deterministic simulator controls against the internal provider contract.

## Phase 0: Research Decisions

All technical unknowns are resolved in [research.md](research.md). The decisive choices are:

1. Use Java 26, Spring Boot 4.1.x, and Resilience4j 2.4.0; pin `resilience4j-spring-boot4` directly and prove startup, configuration binding, Actuator, and Micrometer integration first.
2. Preserve the two-module architecture and keep provider-specific HTTP types at adapter boundaries.
3. Compose the provider pipeline functionally as bounded admission → circuit permission → deadline-aware retry → per-attempt time limit → explicit-timeout `RestClient`, followed by provider-neutral fallback mapping.
4. Count at most one terminal logical provider participation toward circuit health per delivery-options request; retry attempts do not independently accelerate circuit opening.
5. Retry only connection failures, attempt timeouts, permitted `429`, and approved transient `5xx`; never retry no-drivers, validation/deterministic `4xx`, circuit-open, local-admission, permanent, or malformed-quote outcomes.
6. Use an immutable monotonic request deadline, require every retry to fit its delay plus the next attempt, finalize once, and discard late results.
7. Preserve the existing public success shape, generalize it for partial results, and add a safe RFC 9457 `503`; keep detailed unavailability reasons internal.
8. Extend the internal provider contract with an explicit valid no-driver outcome plus controlled `429` and `503` responses.
9. Use provider-scoped structured events and bounded metrics to prove policy behavior without logging request bodies, coordinates, order values, raw exception text, or identifiers as metric tags.
10. Prove behavior with predicate units, deterministic time/jitter tests, MockMvc/OpenAPI contracts, one WireMock boundary per provider, race tests, circuit-transition tests, Compose scenarios, and a bounded failure workload.

## Phase 1: Design Outputs

- [data-model.md](data-model.md) defines request execution, policy snapshots, attempt and participation outcomes, result invariants, validation, aggregation finalization, and circuit transitions.
- [orchestrator-api.yaml](contracts/orchestrator-api.yaml) defines backward-compatible complete/partial `200` results and the controlled all-provider-unavailable `503` problem.
- [provider-api.yaml](contracts/provider-api.yaml) defines available and no-driver `200` outcomes plus deterministic `400`, rate-limit `429`, and technical `503` responses for the internal provider boundary.
- [quickstart.md](quickstart.md) defines reproducible dependency, automated, Compose, retry, partial, circuit, recovery, total-failure, observability, and load validation.

### Post-design constitution re-check

| Gate | Result after design | Evidence |
|---|---|---|
| Useful partial and total-failure behavior | PASS | The public contract and result invariants partition the provider snapshot into valid options and unavailable IDs; zero valid quotes cannot produce `200`. |
| Provider isolation and explicit ordering | PASS | Policy/state entities are keyed by provider; the pipeline order, circuit accounting, admission handling, recovery permits, and fallback mapping are documented and directly testable. |
| Selective bounded retries | PASS | Attempt count is `1..3`; classification and remaining-deadline rules exclude unsafe or impossible retries; quote-only scope is explicit. |
| Domain independence and bounded concurrency | PASS | The internal HTTP contract maps into provider-neutral participation outcomes; per-provider pools/queues and the request deadline are finite and validated. |
| Observable behavior proven by tests | PASS | Quickstart and research cover every constitution-mandated resilience scenario, structured-event safety, bounded metric dimensions, and customer-visible usefulness. |
| Governed stack and MVP scope | PASS with first-task dependency proof | Boot 4.1.x and Resilience4j 2.4.0 are current design inputs; direct starter pinning and startup/metrics proof resolve the known BOM gap without changing the constitution. No deferred persistence, messaging, UI, authentication, real integration, or dispatch enters the design. |
| Public/internal boundary | PASS | The restaurant sees only the orchestrator; provider and scenario operations stay on the internal network. |

Phase 1 introduces no constitution violations and leaves no `NEEDS CLARIFICATION` markers. Implementation may proceed to `$speckit-tasks` after the stale feature-001/003 stack references are reconciled in their own artifacts; independent feature-002 unit, contract, and WireMock work can be sequenced before simulator-backed Compose validation.
