@echo off
echo Pulling all Docker runner images...
echo (This only needs to be done once)
echo.

echo [1/5] Pulling openjdk:17 (Java)...
docker pull openjdk:17

echo [2/5] Pulling python:3.11-slim (Python)...
docker pull python:3.11-slim

echo [3/5] Pulling node:18-slim (JavaScript)...
docker pull node:18-slim

echo [4/5] Pulling gcc:latest (C / C++)...
docker pull gcc:latest

echo.
echo All images ready.
echo Verify with: docker images
pause
