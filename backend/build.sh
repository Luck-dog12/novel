#!/bin/bash

# Build script for Novel Writing Assistant Backend

echo "Building Novel Writing Assistant Backend..."

GRADLE_CMD="gradle"
if [ -x "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
elif ! command -v gradle &> /dev/null; then
    echo "Error: Gradle is not installed and Gradle Wrapper is missing"
    exit 1
fi

# Build the project
echo "Running Gradle build..."
$GRADLE_CMD clean build

if [ $? -ne 0 ]; then
    echo "Error: Build failed"
    exit 1
fi

echo "Build successful!"

# Check if Docker is installed
if command -v docker &> /dev/null; then
    echo "Docker is installed, preparing Docker build..."
    
    # Build Docker image
    echo "Building Docker image..."
    docker build -t novel-writing-assistant-backend .
    
    if [ $? -ne 0 ]; then
        echo "Error: Docker build failed"
        exit 1
    fi
    
    echo "Docker image built successfully!"
    echo "To run the container, use: docker-compose up -d"
else
    echo "Docker is not installed, skipping Docker build"
    echo "To run the application, use: $GRADLE_CMD run"
fi

echo "Build script completed!"
