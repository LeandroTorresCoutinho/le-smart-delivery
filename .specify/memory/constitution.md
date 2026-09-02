<!--
Sync Impact Report
- Version change: template (unversioned) -> 1.0.0
- Modified principles:
  - Placeholder Principle 1 -> I. Useful Results Through Partial Success
  - Placeholder Principle 2 -> II. Provider Isolation and Explicit Resilience
  - Placeholder Principle 3 -> III. Safe Retries and Idempotent Dispatch
  - Placeholder Principle 4 -> IV. Domain Independence and Bounded Concurrency
  - Placeholder Principle 5 -> V. Observable Behavior Proven by Tests
- Added sections:
  - Technical and Product Constraints
  - Development Workflow and Quality Gates
- Removed sections: none
- Follow-up TODOs: none
-->
# Smart Delivery Orchestrator Constitution

## Core Principles

### I. Useful Results Through Partial Success
The quote operation MUST return ranked options whenever at least one provider returns a valid quote.
A slow, failing, rate-limited, or unavailable provider MUST NOT prevent healthy providers from
producing a useful response. Partial responses MUST explicitly identify themselves as partial and
MUST report unavailable providers. When every provider is unavailable, the API MUST return a
controlled `503` problem response rather than an empty success or an unhandled failure. Business
unavailability, such as no drivers, MUST be distinguished from technical failure in behavior and
telemetry. These rules keep customer value, rather than dependency health, as the success measure.

### II. Provider Isolation and Explicit Resilience
Every provider MUST have an independent adapter, HTTP-client configuration, timeout, retry policy,
circuit breaker, fallback behavior, and provider-level telemetry. One provider's failures MUST NOT
open another provider's circuit or consume its resilience budget. Network connection and response
timeouts MUST be configured explicitly; a time limiter alone is insufficient. The effective
composition and execution order of timeout, retry, circuit breaker, and fallback MUST be explicit
in code and verified by tests. An open circuit MUST fail fast without calling the provider. This
isolation prevents a single unhealthy dependency from becoming a platform-wide outage.

### III. Safe Retries and Idempotent Dispatch
Retries MUST be selective, bounded, and reserved for transient conditions such as connection
failures, timeouts, `429`, and approved `5xx` responses. Validation errors and deterministic `4xx`
responses MUST NOT be retried. Retry policies MUST use a small attempt limit, exponential backoff,
and jitter to reduce synchronized load. Quote requests MAY be retried because they are read-like
operations. Delivery creation MUST NOT be retried unless it carries a stable idempotency key; the
order ID is the default idempotency key. These constraints prevent duplicate deliveries and retry
storms while still allowing transient recovery.

### IV. Domain Independence and Bounded Concurrency
Provider-specific contracts MUST be translated at adapter boundaries into provider-neutral domain
models. The scoring and recommendation engine MUST NOT depend on external-provider payload types.
Quote calls MUST run concurrently, but concurrency, queues, timeouts, executors, and thread pools
MUST have explicit bounds. Selection weights for ETA, price, and reliability MUST be configuration,
not hard-coded business rules. A reusable provider simulator SHOULD model providers through
configuration instead of duplicated implementations. These rules make providers replaceable and
keep latency and resource use predictable under failure.

### V. Observable Behavior Proven by Tests
Resilience behavior MUST be proven through observable outcomes, not inferred from annotations or
configuration alone. Automated tests MUST cover all-provider success, partial success, timeout,
transient retry recovery, non-retryable validation failure, circuit opening, open-circuit rejection,
half-open recovery, and controlled total failure. Scoring, normalization, retry classification,
fallback composition, validation, and error mapping MUST have focused unit tests. Logs MUST be
structured JSON, carry correlation and relevant business/provider context, and MUST NOT expose
sensitive customer data. Metrics MUST reveal API usefulness, provider health, latency, errors,
timeouts, retries, circuit state, rejected calls, fallbacks, and partial-result rate. This principle
is non-negotiable because resilience that cannot be observed and reproduced cannot be trusted.

## Technical and Product Constraints

- The initial platform MUST use Java 26, Spring Boot 3, Maven, Resilience4j, Actuator, Micrometer,
  Docker Compose, Kubernetes, GKE Autopilot, Terraform, GitHub Actions, Managed Prometheus, Grafana,
  and Cloud Logging unless an approved amendment changes the stack.
- The MVP MUST remain limited to the orchestrator, one reusable simulator deployed as three
  providers, concurrent quote aggregation, provider-specific resilience, configurable scoring,
  health and resilience telemetry, automated tests, local Compose execution, and GKE deployment.
- The MVP MUST NOT include a user interface, real provider integrations, authentication,
  restaurant onboarding, payments, persistent order management, Pub/Sub flows, or machine-learning
  recommendations. Such work requires a separately specified post-MVP change.
- Only the orchestrator MAY be publicly exposed. Provider simulators and administration endpoints
  MUST remain internal in production-style environments.
- Container images MUST be immutable and tagged by commit SHA; deployments MUST NOT rely on a
  mutable `latest` tag.
- Kubernetes workloads MUST define startup, readiness, and liveness probes, resource requests and
  limits, and an appropriate scaling policy. Internal service calls MUST use stable Kubernetes DNS
  names rather than pod IP addresses.
- Secrets and cloud authentication MUST use short-lived or federated identity mechanisms. GitHub
  Actions deployments to Google Cloud MUST use OIDC with Workload Identity Federation.
- PostgreSQL, Redis, Pub/Sub, persistent dispatch state, and real integrations remain deferred until
  the core quote resilience flow satisfies the MVP success criteria.

## Development Workflow and Quality Gates

1. Each change MUST state the customer-visible behavior, affected provider boundaries, failure
   modes, and measurable acceptance criteria before implementation.
2. Changes to provider calls or resilience configuration MUST include tests that demonstrate the
   effective timeout, retry, circuit-breaker, and fallback sequence, including negative cases.
3. Pull requests MUST compile the complete Maven project and pass unit tests, integration tests,
   static analysis, and dependency checks. Container images MUST build successfully without being
   published from pull-request workflows.
4. Merges to `main` MUST build commit-SHA-tagged images, publish them to Artifact Registry, deploy
   them through documented automation, wait for rollout completion, and pass a smoke test.
5. Changes MUST preserve provider isolation, bounded resource use, safe retry semantics, structured
   telemetry, and the MVP scope. Any exception MUST be documented with its risk, mitigation, and
   approval in the change record.
6. A release candidate for the MVP MUST demonstrate local one-command startup, understandable
   ranked quotes, deterministic provider failure injection, healthy-provider continuity, automated
   resilience evidence, repeatable GKE deployment, and a dashboard showing provider and policy
   behavior.

## Governance

This constitution is the highest-authority engineering policy for the project. Specifications,
plans, tasks, reviews, and implementation decisions MUST comply with it. If another project
document conflicts with this constitution, the constitution governs until it is amended.

Amendments MUST be proposed as an explicit constitution change with a rationale, affected
principles and artifacts, compatibility impact, and any required migration plan. Adoption requires
review and approval by the project maintainer. Amendments take effect only after this file is
updated with the approved text, version, and amendment date.

Versions follow semantic versioning. A MAJOR increment is required for removal or incompatible
redefinition of a principle or governance rule. A MINOR increment is required for a new principle,
new mandatory section, or material expansion of obligations. A PATCH increment is used for
clarifications and non-semantic wording corrections.

Every specification and plan review MUST check constitution compliance. Every pull-request review
MUST verify applicable quality gates and explicitly justify any added complexity. Before a release,
the maintainer MUST audit the release evidence against all principles and the MVP success criteria.
Unapproved exceptions are governance violations and MUST block merge or release until corrected or
ratified through an amendment.

**Version**: 1.0.0 | **Ratified**: 2026-09-01 | **Last Amended**: 2026-09-01
