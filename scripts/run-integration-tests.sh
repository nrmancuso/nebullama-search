#!/usr/bin/env bash
set -euo pipefail

CI="${CI:-false}"
SERVICE_URL="${SERVICE_URL:-http://localhost:8080}"
OPENSEARCH_URL="${OPENSEARCH_URL:-http://localhost:9200}"
MAX_WAIT=120
WAIT_INTERVAL=3

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

docker_compose() {
  docker compose -f "${REPO_ROOT}/docker-compose.yml" "$@"
}

wait_for_http_ok() {
  local url="$1"
  local label="$2"
  local elapsed=0

  until curl -sf "${url}" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
      echo "ERROR: ${label} did not become ready within ${MAX_WAIT}s"
      exit 1
    fi
    sleep "$WAIT_INTERVAL"
    elapsed=$((elapsed + WAIT_INTERVAL))
  done

  echo "${label} is ready."
}

# --- Infrastructure ----------------------------------------------------------

echo "Starting Docker Compose stack..."
docker_compose up -d

echo "Waiting for OpenSearch to be healthy..."
wait_for_http_ok "${OPENSEARCH_URL}/_cluster/health" "OpenSearch"

# --- Ollama models -----------------------------------------------------------

echo "Ensuring Ollama models are pulled..."
"${SCRIPT_DIR}/init.sh"

# --- Service -----------------------------------------------------------------

echo "Waiting for service to be healthy..."
wait_for_http_ok "${SERVICE_URL}/actuator/health" "Service"

# --- Seed data ---------------------------------------------------------------

echo "Seeding test data..."
"${SCRIPT_DIR}/seed-data.sh"

# --- Run tests ---------------------------------------------------------------

echo "Running integration tests..."
cd "${REPO_ROOT}"
./gradlew :integration-tests:test "$@"
TEST_EXIT=$?

# --- Teardown (CI only) ------------------------------------------------------

if [ "${CI}" = "true" ]; then
  echo "CI mode: tearing down..."
  docker_compose down
fi

exit "$TEST_EXIT"
