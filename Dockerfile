# ==============================================================================
# MiniCloud API — Multi-Stage Production Dockerfile
# Stage 1: Build application with Maven
# Stage 2: Minimal, secure JRE runtime container (non-root, headless)
# ==============================================================================

# ── Stage 1: Build ────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and POM files first (optimized layer caching)
COPY mvnw ./
COPY .mvn ./.mvn
COPY pom.xml ./
COPY minicloud-api/pom.xml ./minicloud-api/

# Download dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -pl minicloud-api -am -q

# Copy source code
COPY minicloud-api/src ./minicloud-api/src

# Build the application package
RUN ./mvnw clean package -pl minicloud-api -am -DskipTests -q

# ── Stage 2: Runtime ────
FROM eclipse-temurin:17-jre-alpine

# Security: Create non-root system group and user
RUN addgroup -S minicloud && adduser -S minicloud -G minicloud

WORKDIR /app

# Environment variables for headless production execution
ENV MINICLOUD_MODE=WEB \
    JAVA_OPTS="-Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.awt.headless=true -Dfile.encoding=UTF-8"

# Copy the built JAR from build stage
COPY --from=build /app/minicloud-api/target/*.jar app.jar

# Create persistent data directories and grant ownership to non-root user
RUN mkdir -p minicloud-data/db \
             minicloud-data/storage \
             minicloud-data/lambda-tmp \
             minicloud-data/logs \
             minicloud-data/rds \
    && chown -R minicloud:minicloud /app

USER minicloud

# Expose Spring Boot HTTP port
EXPOSE 8080

# Health check probe against Actuator endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Entrypoint running Spring Boot in headless WEB mode with container JVM flags
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --mode=WEB"]
