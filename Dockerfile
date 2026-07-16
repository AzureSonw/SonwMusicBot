# syntax=docker/dockerfile:1

# Multi-stage build for JMusicBot
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-25 AS builder

ARG BUILD_TIMESTAMP
ENV BUILD_TIMESTAMP=$BUILD_TIMESTAMP

WORKDIR /build

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies with BuildKit cache mount for Maven repository
# This significantly speeds up builds by persisting dependencies between builds
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -B -Pdocker

# Copy source code
COPY src ./src

# Build the application with BuildKit cache mount
RUN --mount=type=cache,target=/root/.m2/repository \
    if [ -n "$BUILD_TIMESTAMP" ]; then \
      mvn clean package -DskipTests -B -Pdocker -Dproject.build.outputTimestamp="$BUILD_TIMESTAMP"; \
    else \
      mvn clean package -DskipTests -B -Pdocker; \
    fi


# Stage 2: Runtime image
# Using Ubuntu Noble (24.04) for libraries required by jdave/udpqueue native libraries
FROM eclipse-temurin:25-jre-noble

# OCI image labels for better traceability and management
LABEL org.opencontainers.image.title="JMusicBot" \
      org.opencontainers.image.description="A cross-platform Discord music bot with a clean interface" \
      org.opencontainers.image.url="https://jmusicbot.com" \
      org.opencontainers.image.source="https://github.com/arif-banai/MusicBot" \
      org.opencontainers.image.vendor="JMusicBot" \
      org.opencontainers.image.licenses="Apache-2.0"

# Create non-root user for security
RUN groupadd --gid 10001 jmusicbot && \
    useradd --uid 10001 --gid 10001 --shell /bin/false jmusicbot

# Create application directories
RUN mkdir -p /app /musicbot && \
    chown -R jmusicbot:jmusicbot /app /musicbot

# Copy the built JAR from builder stage
COPY --from=builder --chown=jmusicbot:jmusicbot /build/target/JMusicBot-*-All.jar /app/app.jar

# Copy and set permissions for entrypoint script in a single layer
COPY --chown=jmusicbot:jmusicbot --chmod=755 docker/entrypoint.sh /app/entrypoint.sh

WORKDIR /musicbot

# Switch to non-root user for security
USER jmusicbot

ENTRYPOINT ["/app/entrypoint.sh"]
