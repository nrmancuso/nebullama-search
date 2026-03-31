#!/usr/bin/env bash
set -euo pipefail

# Ingests seed data in small batches so Ollama doesn't get overwhelmed.
# Verifies every doc was ingested successfully.

SERVICE_URL="${SERVICE_URL:-http://localhost:8080}"
OPENSEARCH_URL="${OPENSEARCH_URL:-http://localhost:9200}"
BATCH_SIZE="${BATCH_SIZE:-5}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../data"

echo "Ingesting seed data into ${SERVICE_URL} (batch size: ${BATCH_SIZE})..."

python3 - "$SERVICE_URL" "$DATA_DIR" "$BATCH_SIZE" << 'PYEOF'
import json, subprocess, sys, os

service_url = sys.argv[1]
data_dir = sys.argv[2]
batch_size = int(sys.argv[3])

types = ["celestial_objects", "missions", "observations", "astronomers", "publications"]
total_ok = 0
total_fail = 0

for type_name in types:
    path = os.path.join(data_dir, f"seed_{type_name}.json")
    if not os.path.exists(path):
        print(f"ERROR: Missing seed file: {path}")
        sys.exit(1)

    with open(path) as f:
        docs = json.load(f)

    ok = 0
    fail = 0
    for i in range(0, len(docs), batch_size):
        batch = docs[i:i + batch_size]
        result = subprocess.run(
            ["curl", "-s", "-X", "POST",
             f"{service_url}/api/v1/ingest/{type_name}/bulk",
             "-H", "Content-Type: application/json",
             "-d", json.dumps(batch)],
            capture_output=True, text=True, timeout=120
        )
        try:
            results = json.loads(result.stdout)
            for r in results:
                if r.get("success"):
                    ok += 1
                else:
                    fail += 1
                    print(f"  WARN: failed doc in {type_name}: {r.get('error', 'unknown')}")
        except (json.JSONDecodeError, TypeError):
            print(f"  ERROR: bad response for {type_name} batch {i // batch_size + 1}: {result.stdout[:200]}")
            sys.exit(1)

    total_ok += ok
    total_fail += fail
    status = "ok" if fail == 0 else f"ok ({fail} failed)"
    print(f"  {type_name}: {ok}/{len(docs)} ingested {status}")

print(f"\nTotal: {total_ok} ingested, {total_fail} failed")
if total_fail > 0:
    print("WARNING: Some docs failed to ingest. Tests may have reduced coverage.")
PYEOF

echo "Refreshing indices..."
curl -s -X POST "${OPENSEARCH_URL}/_all/_refresh" > /dev/null
echo "Seed complete."
