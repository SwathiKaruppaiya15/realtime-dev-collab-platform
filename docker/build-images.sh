#!/bin/bash
# Run this once to build all Docker runner images

echo "Building java-runner..."
docker build -t java-runner ./java-runner

echo "Building python-runner..."
docker build -t python-runner ./python-runner

echo "Building node-runner..."
docker build -t node-runner ./node-runner

echo "All images built successfully."
echo ""
echo "Verify with: docker images | grep runner"
