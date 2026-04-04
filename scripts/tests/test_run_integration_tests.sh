#!/usr/bin/env bash
set -euo pipefail

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

TEST_ROOT="${TMP_DIR}/repo"
BIN_DIR="${TMP_DIR}/bin"
mkdir -p "${TEST_ROOT}/scripts" "${BIN_DIR}"

cp scripts/run-integration-tests.sh "${TEST_ROOT}/scripts/run-integration-tests.sh"

cat > "${TEST_ROOT}/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "./gradlew $*" >> "${CALLS_LOG}"
if [[ "$*" == *":integration-tests:test"* ]]; then
  exit 0
fi
echo "unexpected gradlew invocation: $*" >&2
exit 99
EOF

cat > "${TEST_ROOT}/scripts/init.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo init >> "${CALLS_LOG}"
EOF

cat > "${TEST_ROOT}/scripts/seed-data.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo seed >> "${CALLS_LOG}"
EOF

cat > "${BIN_DIR}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "docker $*" >> "${CALLS_LOG}"
exit 0
EOF

cat > "${BIN_DIR}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "curl $*" >> "${CALLS_LOG}"
if [[ "$*" == *"/_cluster/health"* ]]; then
  exit 0
fi
if [[ "$*" == *"/actuator/health"* ]]; then
  COUNT_FILE="${TEST_TMP}/health_count"
  count=0
  if [[ -f "${COUNT_FILE}" ]]; then
    count="$(cat "${COUNT_FILE}")"
  fi
  count=$((count + 1))
  printf '%s' "${count}" > "${COUNT_FILE}"
  if [[ "${count}" -lt 3 ]]; then
    exit 1
  fi
  printf '{"status":"UP"}'
  exit 0
fi
exit 0
EOF

cat > "${BIN_DIR}/java" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "java should not be called" >&2
exit 99
EOF

chmod +x "${TEST_ROOT}/gradlew" "${TEST_ROOT}/scripts/init.sh" "${TEST_ROOT}/scripts/seed-data.sh"
chmod +x "${BIN_DIR}/docker" "${BIN_DIR}/curl" "${BIN_DIR}/java"

CALLS_LOG="${TMP_DIR}/calls.log"
export CALLS_LOG
export TEST_TMP="${TMP_DIR}"
export PATH="${BIN_DIR}:${PATH}"
export CI="false"
export WAIT_INTERVAL="0"
export MAX_WAIT="1"

(
  cd "${TEST_ROOT}"
  bash scripts/run-integration-tests.sh
)

grep -q "docker compose -f ${TEST_ROOT}/docker-compose.yml up -d" "${CALLS_LOG}"
grep -q "init" "${CALLS_LOG}"
grep -q "seed" "${CALLS_LOG}"
grep -q "/actuator/health" "${CALLS_LOG}"
grep -q "\./gradlew :integration-tests:test" "${CALLS_LOG}"

if grep -q "bootRun" "${CALLS_LOG}"; then
  echo "bootRun fallback was invoked unexpectedly" >&2
  exit 1
fi
