# Multi-stage Dockerfile for Screenshot API Service

# Stage 1: Build stage
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradle/ gradle/
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./

# Download dependencies first (better caching)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src/ src/

# Build the application
RUN ./gradlew build --no-daemon -x test

# Stage 2: Runtime stage with better Playwright support
FROM eclipse-temurin:21-jre-jammy

# Install dependencies for Playwright
RUN apt-get update && apt-get install -y \
    ca-certificates \
    chromium-browser \
    curl \
    fontconfig \
    fonts-liberation \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcairo2 \
    libdrm2 \
    libgbm1 \
    libgdk-pixbuf2.0-0 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libpango-1.0-0 \
    libx11-6 \
    libx11-xcb1 \
    libxcb1 \
    libxcomposite1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxss1 \
    libxtst6 \
    nodejs \
    npm \
    wget \
    xdg-utils \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 1000 appuser \
    && useradd --uid 1000 --gid appuser --shell /bin/bash --create-home appuser

# Create directories
RUN mkdir -p /app/screenshots /app/logs \
    && chown -R appuser:appuser /app

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/*-all.jar app.jar

# Set environment variables
ENV PLAYWRIGHT_BROWSERS_PATH=/app/.cache/ms-playwright \
    PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium-browser \
    CHROMIUM_FLAGS="--no-sandbox --disable-setuid-sandbox --disable-dev-shm-usage" \
    DEBIAN_FRONTEND=noninteractive

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Start the application
CMD ["java", "-jar", "-Xmx512m", "-Xms256m", "app.jar"]