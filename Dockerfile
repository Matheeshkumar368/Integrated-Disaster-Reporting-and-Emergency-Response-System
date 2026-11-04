# ========================================================
# Dockerfile: Disaster Management & Alert System
# Multi-stage production container build
# Base: Eclipse Temurin OpenJDK 21
# ========================================================

# Stage 1: Build Application Artifact
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copy build definition and source files
COPY pom.xml .
COPY src src

# Package production executable JAR without running tests during image assembly
RUN mvn clean package -DskipTests

# Stage 2: Minimal Production Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root system user for security hardening
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Copy JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

# Port Configuration
EXPOSE 8080

# Production Environment Defaults
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

# Healthcheck probe using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

