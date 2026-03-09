# =============================================================================
# Multi-stage Dockerfile for the URL Shortener backend (Dropwizard + Java 17)
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build the fat JAR with Maven
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Cache dependency downloads separately from the source copy so that
# unchanged pom.xml doesn't invalidate the layer on every code change.
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B --no-transfer-progress

# ---------------------------------------------------------------------------
# Stage 2: Lean runtime image
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the shaded (fat) JAR produced by the build stage
COPY --from=builder /build/target/url-shortener-service-1.0.0.jar app.jar

# Copy the Dropwizard config
COPY src/main/config/config.yml config.yml

# Application port (HTTP) and admin port
EXPOSE 8080 8081

# Health-check against the Dropwizard admin ping endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8081/healthcheck || exit 1

ENTRYPOINT ["java", "-jar", "app.jar", "server", "config.yml"]
