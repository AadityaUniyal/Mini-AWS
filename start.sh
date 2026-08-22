#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
MODE="DESKTOP"

# ── Display Detection for Linux / macOS ──────────────────────────────
HAS_DISPLAY=false
if [ -n "$DISPLAY" ] || [ -n "$WAYLAND_DISPLAY" ] || [ "$(uname)" = "Darwin" ]; then
    HAS_DISPLAY=true
fi

# ── Parse Arguments or Interactive Prompt ────────────────────────────
if [[ "$1" =~ ^[Dd][Ee][Ss][Kk][Tt][Oo][Pp]$ ]]; then
    MODE="DESKTOP"
elif [[ "$1" =~ ^[Ww][Ee][Bb]$ ]]; then
    MODE="WEB"
elif [ "$#" -eq 0 ]; then
    if [ "$HAS_DISPLAY" = false ]; then
        echo "No graphical display detected ($DISPLAY unset). Defaulting to WEB mode."
        MODE="WEB"
    else
        echo "============================================================"
        echo "  MiniCloud -- Java Cloud Platform (Modular Monolith)"
        echo "============================================================"
        echo "Select startup mode:"
        echo "  [1] Desktop UI (Java Swing Dashboard + Embedded API) [default]"
        echo "  [2] Headless Web Service (REST API + Swagger UI)"
        read -r -p "Enter choice [1-2] (default 1): " CHOICE
        if [ "$CHOICE" = "2" ]; then
            MODE="WEB"
        else
            MODE="DESKTOP"
        fi
    fi
fi

# Auto-downgrade to WEB if DESKTOP was chosen without a display
if [ "$MODE" = "DESKTOP" ] && [ "$HAS_DISPLAY" = false ]; then
    echo "WARNING: Graphical display not detected. Switching to WEB (headless) mode."
    MODE="WEB"
fi

echo ""
echo "Selected Mode: $MODE"
echo "Building MiniCloud..."
cd "$ROOT"

# Ensure mvnw has execute permissions
chmod +x "$ROOT/mvnw" 2>/dev/null || true

./mvnw clean package -pl minicloud-api -am -DskipTests -q

echo "Build successful!"
echo "Starting MiniCloud in $MODE mode..."
cd "$ROOT/minicloud-api"

JVM_OPTS="-Xmx512m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8"

if [ "$MODE" = "WEB" ]; then
    JVM_OPTS="$JVM_OPTS -Djava.awt.headless=true"
fi

exec java $JVM_OPTS -jar target/minicloud-api-1.0.0.jar --mode="$MODE"