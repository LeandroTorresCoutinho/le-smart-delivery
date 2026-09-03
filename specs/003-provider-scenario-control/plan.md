# Implementation Plan: Deterministic Provider Scenario Control

**Branch**: `002-provider-scenario-control` | **Date**: 2026-09-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-provider-scenario-control/spec.md`

**Status**: Blocked at the pre-Phase 0 Constitution Check

## Summary

Provide one reusable delivery-provider simulator whose independently deployed provider instances can activate, inspect, replay, and reset deterministic quote scenarios. The intended design keeps validated scenario configuration and progression in memory per provider instance, exposes administration only over the simulator's internal operational boundary, and preserves distinct valid-quote, technical-failure, rate-limit, and no-driver business outcomes.

Planning cannot proceed to research consolidation or Phase 1 design because the constitution mandates a technology combination with no officially supported release pairing as of 2026-09-01.

## Technical Context

**Language/Version**: Java 26, mandated by the project constitution

**Primary Dependencies**: Spring Boot 3, Maven, Spring MVC, Jakarta Bean Validation, Actuator, and Micrometer; exact dependency resolution is blocked because the current Spring Boot 3 release line does not officially support Java 26

**Storage**: Process-local in-memory scenario state only; no database, cache, or cross-instance persistence

**Testing**: JUnit 5, Spring Boot test support, controller contract tests, deterministic concurrency tests, and Docker Compose end-to-end validation

**Target Platform**: Linux containers for local Docker Compose and Kubernetes/GKE Autopilot

**Project Type**: Maven multi-module web-service repository with a reusable `provider-simulator` application deployed as three independently named provider instances

**Performance Goals**: Scenario activation affects the next quote decision; state claims remain deterministic under overlapping requests; `NORMAL` adds no intentional delay; `SLOW` supports 0 through 30 seconds; administration remains responsive enough for an operator to configure, inspect, and restore a provider within two minutes

**Constraints**: Per-provider state isolation; atomic validation and replacement; unique ordered sequence positions under concurrency; bounded request execution; internal-only administration; no persistent scenario state; no customer request contents or secrets in inspection or errors

**Scale/Scope**: Seven scenarios, three MVP provider instances, patterns up to 100 positions, temporary-failure counts up to 100, and percentage configurations from 0 through 100

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle or constraint | Result | Evidence |
|---|---|---|
| I. Useful Results Through Partial Success | PASS | The simulator contract preserves separate valid quote, technical failure, rate-limit, and no-driver business outcomes for the orchestrator to classify. |
| II. Provider Isolation and Explicit Resilience | PASS | Scenario state belongs to one provider instance; no configuration or counter is shared across providers. |
| III. Safe Retries and Idempotent Dispatch | PASS | Quote simulation remains read-like; validation failures remain deterministic client failures and no delivery creation is introduced. |
| IV. Domain Independence and Bounded Concurrency | PASS | The feature extends the mandated reusable simulator and requires bounded, deterministic concurrent state claims. |
| V. Observable Behavior Proven by Tests | PASS | The specification requires automated evidence for every scenario, boundary, replay, race, inspection, reset, and isolation outcome. |
| MVP and exposure boundaries | PASS | No UI, authentication, persistence, real integration, or public simulator administration is added. |
| Mandated Java 26 and Spring Boot 3 pairing | **FAIL** | [Spring Boot 3.5.16 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) support Java only through version 25. [Spring Boot 4.1.1 system requirements](https://docs.spring.io/spring-boot/system-requirements.html) are the current line supporting Java 26, which violates the constitution's Spring Boot 3 mandate. |

**Gate result**: **FAILED**. An experimental compile spike cannot create official framework support and therefore does not resolve this governance conflict.

### Required Governance Decision

One approved constitution amendment is required before Phase 0:

1. Change Java 26 to Java 25 and use Spring Boot 3.5.x. This is the smallest platform change and preserves the mandated Spring generation.
2. Change Spring Boot 3 to Spring Boot 4.1.x and retain Java 26. This preserves the Java mandate but expands framework and Jakarta compatibility work.

No exception or amendment has been assumed. Per the constitution, the unresolved violation blocks planning and implementation.

## Project Structure

Phase 1 source and contract structure has not been finalized because the pre-design gate failed.

```text
specs/003-provider-scenario-control/
├── spec.md
├── plan.md              # Gate result recorded here
└── checklists/
    └── requirements.md
```

The anticipated repository boundary remains the constitution's Maven multi-module structure with `provider-simulator/`, `delivery-orchestrator/`, `compose.yaml`, and internal-only Kubernetes provider services, but this is not yet an approved Phase 1 design.

## Complexity Tracking

No constitutional exception is proposed or justified. The technology conflict requires an amendment before complexity trade-offs can be evaluated.
