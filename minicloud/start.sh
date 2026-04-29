#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
MODE="DESKTOP"  # Default changed to DESKTOP

if [[ "$1" =~ ^[Dd][Ee][Ss][Kk][Tt][Oo][Pp]$ ]]; then
    MODE="DESKTOP"
elif [[ "$1" =~ ^[Ww][Ee][Bb]$ ]]; then
    MODE="WEB"
else
    echo "Select mode:"
    echo "  1. DESKTOP (Swing UI) [default]"
    echo "  2. WEB (headless REST API)"
    read -p "Enter choice [1-2] (default 1): " CHOICE
    if [ "$CHOICE" = "2" ]; then MODE="WEB"; fi
fi

echo "Building..."
cd "$ROOT"
./mvnw clean package -pl minicloud-api -am -DskipTests -q

echo "Starting MiniCloud in $MODE mode..."
cd "$ROOT/minicloud-api"
java -Xmx512m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC \
     -jar target/minicloud-api-1.0.0.jar --mode=$MODE