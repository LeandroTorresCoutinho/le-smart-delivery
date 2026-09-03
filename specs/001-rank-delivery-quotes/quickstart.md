# Phase 1 Quickstart: Validate Ranked Delivery Quotes

This guide defines the runnable acceptance flow for feature `001-rank-delivery-quotes` after its implementation tasks are complete. It validates the contracts in [contracts/](contracts/) and the rules in [data-model.md](data-model.md); it does not replace the automated test suite.

## Prerequisites

- JDK 26 without preview features
- Docker with Docker Compose
- `curl` and `jq`
- Bash or zsh for the Maven Wrapper and validation commands

The repository supplies Maven through `./mvnw`; no global Maven installation is required.

## 1. Prove the Governed Stack First

The constitution requires Java 26 with Spring Boot 4.1.x. The build pins Spring Boot 4.1.1 and `resilience4j-spring-boot4:2.4.0`; before feature implementation proceeds beyond the skeleton, run the mandatory governed-stack gate:

```bash
java -version
./mvnw --version
./mvnw clean verify
```

The gate passes only when all of the following work on local Linux-container builds and CI with JDK 26:

- both application contexts start;
- JSON request and response serialization works;
- Jakarta Validation and RFC 9457 problem rendering work;
- `RestClient` performs an internal quote exchange;
- Actuator health and Micrometer registries initialize;
- Resilience4j provider registries and validated configuration initialize;
- no linkage, class-version, agent, or build-plugin incompatibility occurs.

If the gate fails, stop. Do not change Java, Spring Boot, or the Resilience4j Boot integration silently; document the failure and reconcile the governed stack as described in [research.md](research.md).

## 2. Run Automated Verification

```bash
./mvnw clean verify
```

Expected result:

- both Maven modules compile and package;
- unit, HTTP contract, WireMock integration, and concurrency tests pass;
- invalid requests prove that no provider adapter is invoked;
- scoring, two-decimal rounding, ordering, ties, recommendation, and correlation cases pass;
- structured-log records parse as JSON and omit prohibited request data;
- Micrometer tests use bounded tags and expose expected counters/timers;
- formatting, static analysis, dependency checks, and build rules pass.

## 3. Start the Packaged System

```bash
docker compose up --build -d
docker compose ps
```

Compose starts one orchestrator and three instances of the same simulator image. Each simulator has a different configured provider ID and deterministic NORMAL quote profile. Only the orchestrator binds a public host port.

Wait until the orchestrator and all three simulator health checks are healthy:

```bash
curl --fail --silent http://localhost:8080/actuator/health
```

Expected orchestrator status: `UP`. Provider health is checked by Compose over the internal network; provider ports and administration interfaces are not exposed publicly.

## 4. Validate the Complete Ranked Result

```bash
curl --silent --show-error \
  --dump-header /tmp/le-smart-delivery-headers.txt \
  --output /tmp/le-smart-delivery-result.json \
  --request POST http://localhost:8080/api/v1/delivery-options \
  --header 'Content-Type: application/json' \
  --header 'X-Correlation-Id: acceptance-001' \
  --data '{
    "restaurantId": "restaurant-123",
    "orderId": "order-93821",
    "orderValue": 89.90,
    "pickup": {
      "latitude": -22.9068,
      "longitude": -43.1729
    },
    "destination": {
      "latitude": -22.9519,
      "longitude": -43.2105
    }
  }'
```

Check the response invariants:

```bash
jq -e '
  .correlationId == "acceptance-001" and
  .currency == "BRL" and
  (.options | length) == 3 and
  .recommendedProvider == .options[0].provider and
  .partialResult == false and
  .unavailableProviders == [] and
  ([.options[].provider] | unique | length) == 3 and
  ([.options[].score] == ([.options[].score] | sort | reverse))
' /tmp/le-smart-delivery-result.json
```

Also verify that `X-Correlation-Id: acceptance-001` appears once in the response headers. Each option must match the public schema in [orchestrator-api.yaml](contracts/orchestrator-api.yaml), with a non-negative two-decimal fee, positive whole estimated minutes, and a score from `0.00` to `100.00`.

The simple score-order assertion above covers the primary ordering. Automated tests cover equal-score ETA, fee, and provider-ID tie breakers because those require controlled quote fixtures.

## 5. Validate Determinism

Repeat the request with the same header and body, save the second body, and compare canonical JSON:

```bash
curl --silent --show-error \
  --output /tmp/le-smart-delivery-result-repeat.json \
  --request POST http://localhost:8080/api/v1/delivery-options \
  --header 'Content-Type: application/json' \
  --header 'X-Correlation-Id: acceptance-001' \
  --data '{
    "restaurantId": "restaurant-123",
    "orderId": "order-93821",
    "orderValue": 89.90,
    "pickup": {"latitude": -22.9068, "longitude": -43.1729},
    "destination": {"latitude": -22.9519, "longitude": -43.2105}
  }'

diff \
  <(jq --sort-keys . /tmp/le-smart-delivery-result.json) \
  <(jq --sort-keys . /tmp/le-smart-delivery-result-repeat.json)
```

Expected result: no difference.

## 6. Validate Generated Correlation

Submit the same valid request without `X-Correlation-Id`. The result body and response header must contain the same generated UUIDv4. The value must also appear on all three internal provider requests and structured request-completion events.

Automated contract tests additionally prove that a valid caller value is preserved exactly and malformed or duplicate headers return a controlled validation problem with a newly generated safe identifier.

## 7. Validate All Field Errors and Zero Provider Calls

Record each provider's quote-request counter, then submit one request with multiple invalid fields:

```bash
curl --silent --show-error \
  --output /tmp/le-smart-delivery-problem.json \
  --write-out '%{http_code}\n' \
  --request POST http://localhost:8080/api/v1/delivery-options \
  --header 'Content-Type: application/json' \
  --header 'X-Correlation-Id: validation-001' \
  --data '{
    "restaurantId": "",
    "orderId": "order-invalid",
    "orderValue": 0,
    "pickup": {"latitude": 91, "longitude": -181},
    "destination": {"latitude": -22.9519, "longitude": -43.2105}
  }'
```

Expected HTTP status: `400` with `Content-Type: application/problem+json`.

```bash
jq -e '
  .status == 400 and
  .correlationId == "validation-001" and
  ([.errors[].source.pointer] | index("#/restaurantId")) != null and
  ([.errors[].source.pointer] | index("#/orderValue")) != null and
  ([.errors[].source.pointer] | index("#/pickup/latitude")) != null and
  ([.errors[].source.pointer] | index("#/pickup/longitude")) != null
' /tmp/le-smart-delivery-problem.json
```

After the request, every provider quote-request counter must be unchanged. Automated integration tests use WireMock request verification as the authoritative proof; local metrics provide visible supporting evidence.

## 8. Validate Configuration-Driven Ranking

Restart the orchestrator with a valid alternative weight set while leaving all simulator quote profiles unchanged:

```bash
DELIVERY_RANKING_ETA_WEIGHT=20 \
DELIVERY_RANKING_PRICE_WEIGHT=70 \
DELIVERY_RANKING_RELIABILITY_WEIGHT=10 \
docker compose up --build -d --force-recreate delivery-orchestrator
```

Repeat the valid request. Expected result:

- submitted order information is unchanged;
- all three providers remain present;
- scores and ordering match the alternative weights and deterministic tie rules;
- exactly one recommendation still equals the first option.

Then restore the default `45/35/20` configuration. A weight total other than `100` must fail startup validation rather than serve misleading rankings.

## 9. Validate the Customer Response Target

Use the repository script to apply the fixed acceptance workload:

```bash
./scripts/generate-load.sh \
  --url http://localhost:8080/api/v1/delivery-options \
  --warmup 20 \
  --requests 200 \
  --concurrency 10
```

Record the JDK, container images, host resources, Compose configuration, and reported p50/p95/p99. The feature passes SC-005 when:

- all 200 measured responses are successful and complete;
- every response contains three correctly ordered options and one correct recommendation;
- at least 95% of measured responses complete within 2 seconds.

The functional latch/barrier test remains the proof that provider calls fan out concurrently; this workload proves only the user-visible latency target under the documented environment.

## 10. Inspect Safe Observability

Inspect orchestrator logs and metrics after the scenarios:

```bash
docker compose logs delivery-orchestrator
curl --silent http://localhost:8080/actuator/prometheus
```

Expected evidence:

- structured JSON events for validation rejection, provider quote completion, ranking completion, and request completion;
- correlation, opaque restaurant/order identifiers, provider, outcome, option count, recommendation, completeness, configuration version, and duration where applicable;
- no coordinates, order values, request bodies, or secrets;
- no correlation, restaurant, or order IDs in metric tags;
- bounded provider, outcome, completeness, and validation-field dimensions.

## 11. Stop the Environment

```bash
docker compose down
```

This feature creates no persistent volumes or durable order data.

## Release Boundary

Passing this guide proves feature 001's complete successful flow. It does not authorize production release. Feature `003-provider-failure-isolation` must still add and prove independent timeout, selective retry, circuit breaker, fallback, partial-result, and controlled total-failure behavior required by the constitution.
