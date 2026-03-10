# Use Gradle image for build stage
FROM gradle:7.6.0-jdk11 AS build

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Build project
RUN gradle clean installDist --no-daemon

# Use AdoptOpenJDK 11 as base image for runtime
FROM adoptopenjdk:11-jre-hotspot

# Set working directory
WORKDIR /app

# Copy built distribution from build stage
COPY --from=build /app/build/install/backend/ ./

# Expose port
EXPOSE 8080

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run application
ENTRYPOINT ["sh", "-c", "/app/bin/backend"]
