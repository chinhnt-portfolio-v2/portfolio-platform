#!/usr/bin/env bash
# =============================================================================
# Smoke Tests — deploy script
# Delegated by deploy.yml after Cloud Run deployment completes.
# =============================================================================
# Primary path: Jest smoke test suite (Node.js, via npm run smoke:test)
# Fallback path: Legacy curl-based bash checks (no Node.js available)
#
# CI integration (Option B — inline):
#   deploy.yml calls this script as the final step. If npm is available,
#   it runs the Jest suite against $DEPLOYED_BE_URL.
#   If Node.js is not available, falls back to curl health checks.
#
# Separate smoke.yml workflow (Option A — future enhancement):
#   smoke.yml is triggered by workflow_run from deploy.yml. It downloads
#   the deployed-url artifact and runs npm run smoke:test independently.
# =============================================================================

set -e

# BASE_URL is passed by deploy.yml; DEPLOYED_BE_URL is used by smoke.yml workflow_run
HOST="${BASE_URL:-${DEPLOYED_BE_URL:-http://localhost:8080}}"
RETRIES=3
SLEEP=5

# ── Helper: curl health check ─────────────────────────────────────────────────

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
    return 1
}

# ── Primary path: Jest smoke test suite ──────────────────────────────────────

# Check if npm and Node.js are available and smoke:test script exists
if command -v npm > /dev/null 2>&1 && [ -f "$(dirname "$0")/../package.json" ]; then

    echo "Node.js smoke test suite detected — running npm run smoke:test ..."

    # Verify the smoke:test script is defined (guards against missing package.json)
    SMOKE_DEFINED=false
    npm run smoke:test --silent -- --listTests > /dev/null 2>&1 && SMOKE_DEFINED=true
    if [ "$SMOKE_DEFINED" = "true" ]; then

        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "Running REST + WebSocket smoke tests against: $HOST"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

        export DEPLOYED_BE_URL="$HOST"
        # SMOKE_TEST_ADMIN_TOKEN is picked up from the environment (set in deploy.yml or CI secrets)

        npm run smoke:test
        SMOKE_EXIT=$?

        if [ $SMOKE_EXIT -eq 0 ]; then
            echo ""
            echo "✅ All smoke tests passed."
            exit 0
        else
            echo ""
            echo "❌ Smoke tests failed (exit code: $SMOKE_EXIT)."
            echo "   The CI gate has failed — investigate before sending production traffic."
            exit $SMOKE_EXIT
        fi
    fi

    # Curl checks run only if smoke:test is not defined
    echo "⚠  smoke:test script not found in package.json — falling back to curl checks."
fi

# ── Fallback path: Legacy curl-based smoke checks ─────────────────────────────

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Running legacy curl smoke tests against: $HOST"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

check_endpoint "$HOST/actuator/health" "actuator/health"
check_endpoint "$HOST/api/v1/project-health" "api/v1/project-health"

echo "All legacy smoke tests passed."
