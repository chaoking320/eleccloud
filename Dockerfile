# ==============================================================================
# Multi-stage Dockerfile for ElecCloud (Out-of-the-Box Docker Compose Build)
# Stage 1 (builder) is shared and cached across retry-server, retry-admin, and retry-example.
# Note: Individual module Dockerfiles (retry-*/Dockerfile) are preserved for fast pre-built JAR deployment.
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

# 1. Copy POM files first to leverage Docker layer caching for dependencies
COPY pom.xml ./
COPY retry-client-sdk/pom.xml retry-client-sdk/pom.xml
COPY retry-server/pom.xml retry-server/pom.xml
COPY retry-admin/pom.xml retry-admin/pom.xml
COPY retry-example/pom.xml retry-example/pom.xml

# 2. Copy source code and build all modules in a single cached reactor pass
COPY retry-client-sdk/src retry-client-sdk/src
COPY retry-server/src retry-server/src
COPY retry-admin/src retry-admin/src
COPY retry-example/src retry-example/src

RUN mvn clean package -DskipTests -B && \
    mkdir -p /out && \
    cp retry-server/target/retry-server-*-exec.jar /out/retry-server.jar && \
    cp retry-admin/target/retry-admin-*.jar /out/retry-admin.jar && \
    cp retry-example/target/retry-example-*.jar /out/retry-example.jar

# Stage 2: Lightweight JRE runtime
FROM eclipse-temurin:17-jre
VOLUME /tmp
ARG APP_MODULE=retry-server
COPY --from=builder /out/${APP_MODULE}.jar /app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
