#!/bin/bash
set -e

HOST="${BASE_URL:-http://localhost:8080}"
RETRIES=3
SLEEP=5

check_endpoint() {
    local url=$1
    local name=$2
    for i in $(seq 1 $RETRIES); do
        if curl -sf "$url" > /dev/null 2>&1; then
            echo "OK: $name"
            return 0
        fi
        echo "Attempt $i/$RETRIES failed for $name — retrying in ${SLEEP}s..."
        sleep $SLEEP
    done
    echo "FAIL: $name did not respond after $RETRIES attempts" >&2
    exit 1
}

check_endpoint "$HOST/actuator/health" "actuator/health"
check_endpoint "$HOST/api/v1/project-health" "api/v1/project-health"

echo "All smoke tests passed."
