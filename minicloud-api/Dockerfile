# ── Stage 1: Build ────
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and POM files first (for layer caching)
COPY ../mvnw ./
COPY ../.mvn ./.mvn
COPY ../pom.xml ./
COPY pom.xml ./minicloud-api/

# Download dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -pl minicloud-api -am -q

# Copy source code
COPY src ./minicloud-api/src

# Build the application
RUN ./mvnw clean package -pl minicloud-api -am -DskipTests -q

# ── Stage 2: Runtime ────
FROM eclipse-temurin:17-jre-alpine

# Security: run as non-root user
RUN addgroup -S minicloud && adduser -S minicloud -G minicloud

WORKDIR /app

# Copy the built JAR
COPY --from=build /app/minicloud-api/target/*.jar app.jar

# Create data directories
RUN mkdir -p minicloud-data/db minicloud-data/storage minicloud-data/lambda-tmp minicloud-data/logs minicloud-data/rds \
    && chown -R minicloud:minicloud /app

USER minicloud

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENTRYPOINT ["java", "-Xmx512m", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", "-jar", "app.jar"]
