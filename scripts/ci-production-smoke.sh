#!/usr/bin/env bash
set -euo pipefail

mkdir -p backend/target
java -jar backend/target/smart-cafe-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 \
  > backend/target/production-smoke.log 2>&1 &
backend_pid=$!
trap 'kill "$backend_pid" 2>/dev/null || true; wait "$backend_pid" 2>/dev/null || true' EXIT

for ((attempt = 1; attempt <= 60; attempt++)); do
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo '::error::Spring Boot exited before the production smoke test.'
    exit 1
  fi
  if curl --fail --silent --max-time 1 "http://127.0.0.1:${SERVER_PORT:?}/actuator/health" >/dev/null; then
    cd frontend
    npm exec -- playwright test --config=playwright.smoke.config.js
    exit
  fi
  sleep 1
done

echo '::error::Spring Boot health endpoint did not become ready.'
exit 1
