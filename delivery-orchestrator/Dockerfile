# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:26-jdk-alpine@sha256:e5b0a876436e5d1c8ff84dfd091cb530b83a9ed4273317d24acb673695bb7e0c AS build

WORKDIR /workspace

COPY --chmod=0755 mvnw mvnw
COPY .mvn/ .mvn/
COPY pom.xml pom.xml
COPY delivery-orchestrator/pom.xml delivery-orchestrator/pom.xml
COPY provider-simulator/pom.xml provider-simulator/pom.xml
RUN ./mvnw --batch-mode --no-transfer-progress \
    --projects delivery-orchestrator --also-make dependency:go-offline

COPY delivery-orchestrator/src/ delivery-orchestrator/src/
RUN ./mvnw --batch-mode --no-transfer-progress \
    --projects delivery-orchestrator --also-make package -DskipTests

FROM eclipse-temurin:26-jre-alpine@sha256:2c984601c59ac93f97947ce66d03df25c2e16bed97a28289fbc0ab157087f1b5

ARG BUILD_DATE=1970-01-01T00:00:00Z
ARG IMAGE_VERSION=local
ARG VCS_REF=local
ARG SOURCE_URL=https://github.com/LeandroTorresCoutinho/le-smart-delivery

LABEL org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.description="Public ranked delivery-options orchestrator" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}" \
      org.opencontainers.image.title="LeSmartDelivery Delivery Orchestrator" \
      org.opencontainers.image.version="${IMAGE_VERSION}"

RUN addgroup -S -g 10001 app \
    && adduser -S -D -H -u 10001 -G app app

WORKDIR /app
COPY --from=build --chown=10001:10001 \
    /workspace/delivery-orchestrator/target/delivery-orchestrator-*.jar application.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/application.jar"]
