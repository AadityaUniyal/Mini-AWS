#!/bin/bash

echo "=== MiniCloud Launcher ==="
echo "Select mode:"
echo "  1. WEB (headless REST API)"
echo "  2. DESKTOP (Swing UI)"
read -p "Enter choice [1-2] (default 1): " CHOICE

MODE="WEB"
if [ "$CHOICE" = "2" ]; then
    MODE="DESKTOP"
fi

echo "Building..."
./mvnw clean package -pl minicloud-api -am -DskipTests -q

echo "Starting in $MODE mode..."
java -Xmx512m -XX:MaxMetaspaceSize=256m -jar \
    minicloud-api/target/minicloud-api-1.0.0.jar --mode=$MODE