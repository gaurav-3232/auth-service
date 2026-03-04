# ============================================================
# Stage 1: Build
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install curl + unzip (needed by mvnw to download Maven)
RUN apk add --no-cache curl unzip

# Cache dependencies first (layer caching optimization)
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Build the app
COPY src ./src
RUN ./mvnw package -DskipTests -B && \
    mv target/*.jar target/app.jar

# ============================================================
# Stage 2: Production runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Security: non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# curl for container healthcheck
RUN apk add --no-cache curl

# Copy only the built JAR
COPY --from=build --chown=appuser:appgroup /app/target/app.jar app.jar

USER appuser

EXPOSE 8080

# Container healthcheck — hits Spring Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]