#!/usr/bin/env bash
set -euo pipefail

OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
MAX_WAIT=120
WAIT_INTERVAL=3

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Checking Docker Compose services..."
if ! docker compose -f "${REPO_ROOT}/docker-compose.yml" ps --services --filter status=running 2>/dev/null | grep -q .; then
  echo "Starting Docker Compose stack..."
  docker compose -f "${REPO_ROOT}/docker-compose.yml" up -d
fi

echo "Waiting for Ollama to be ready at ${OLLAMA_URL}..."
elapsed=0
until curl -sf "${OLLAMA_URL}/api/tags" > /dev/null 2>&1; do
  if [ "$elapsed" -ge "$MAX_WAIT" ]; then
    echo "ERROR: Ollama did not become ready within ${MAX_WAIT}s"
    exit 1
  fi
  sleep "$WAIT_INTERVAL"
  elapsed=$((elapsed + WAIT_INTERVAL))
done
echo "Ollama is ready."

pull_model() {
  local model="$1"
  echo "Checking model: ${model}..."
  if curl -sf "${OLLAMA_URL}/api/tags" | grep -q "\"${model}\""; then
    echo "  ${model} already present, skipping."
  else
    echo "  Pulling ${model}..."
    curl -sf -X POST "${OLLAMA_URL}/api/pull" \
      -H "Content-Type: application/json" \
      -d "{\"name\": \"${model}\"}" | tail -1
    echo "  Done."
  fi
}

pull_model "nomic-embed-text"
pull_model "mistral:7b"

echo "All models ready."
