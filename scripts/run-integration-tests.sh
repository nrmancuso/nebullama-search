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
  if [ "${CI}" = "true" ]; then
    docker compose -f "${REPO_ROOT}/docker-compose.yml" "$@"
  else
    docker compose -f "${REPO_ROOT}/docker-compose.yml" --profile local "$@"
  fi
}

# --- Infrastructure ----------------------------------------------------------

echo "Checking Docker Compose services..."
if ! docker_compose ps --services --filter status=running 2>/dev/null | grep -q "opensearch"; then
  echo "Starting Docker Compose stack..."
  docker_compose up -d

  echo "Waiting for OpenSearch to be healthy..."
  elapsed=0
  until curl -sf "${OPENSEARCH_URL}/_cluster/health" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
      echo "ERROR: OpenSearch did not become ready within ${MAX_WAIT}s"
      exit 1
    fi
    sleep "$WAIT_INTERVAL"
    elapsed=$((elapsed + WAIT_INTERVAL))
  done
  echo "OpenSearch is ready."
fi

# --- Ollama models -----------------------------------------------------------

echo "Ensuring Ollama models are pulled..."
"${SCRIPT_DIR}/init.sh"

# --- Service -----------------------------------------------------------------

if ! curl -sf "${SERVICE_URL}/actuator/health" > /dev/null 2>&1; then
  echo "Starting nebullama-search service..."
  OLLAMA_TIMEOUT="${OLLAMA_READ_TIMEOUT_MS:-60000}"
  if [ "${CI}" = "true" ]; then
    cd "${REPO_ROOT}"
    JAR_PATH="$(find "${REPO_ROOT}/service/build/libs" -maxdepth 1 -name '*.jar' | head -n 1)"
    if [ -z "${JAR_PATH}" ]; then
      ./gradlew :service:bootJar
      JAR_PATH="$(find "${REPO_ROOT}/service/build/libs" -maxdepth 1 -name '*.jar' | head -n 1)"
    fi
    if [ -z "${JAR_PATH}" ]; then
      echo "ERROR: service bootJar completed but no jar was found."
      exit 1
    fi
    java -jar "${JAR_PATH}" --ollama.read-timeout-ms="${OLLAMA_TIMEOUT}" &
    SERVICE_PID=$!
  else
    cd "${REPO_ROOT}" && ./gradlew :service:bootRun \
      --args="--ollama.read-timeout-ms=${OLLAMA_TIMEOUT}" &
    SERVICE_PID=$!
  fi

  echo "Waiting for service to be healthy..."
  elapsed=0
  until curl -sf "${SERVICE_URL}/actuator/health" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
      echo "ERROR: Service did not become ready within ${MAX_WAIT}s"
      exit 1
    fi
    sleep "$WAIT_INTERVAL"
    elapsed=$((elapsed + WAIT_INTERVAL))
  done
  echo "Service is ready."
fi

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
  if [ -n "${SERVICE_PID:-}" ]; then
    kill "$SERVICE_PID" 2>/dev/null || true
  fi
  docker_compose down
fi

exit "$TEST_EXIT"
