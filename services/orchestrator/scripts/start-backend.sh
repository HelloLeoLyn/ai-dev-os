#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${script_dir}/.." && pwd)"

if ! command -v java >/dev/null 2>&1; then
  echo "Java 21 is required but java was not found in PATH." >&2
  exit 1
fi

if [[ ! -x "${project_dir}/mvnw" ]]; then
  echo "Maven Wrapper is not executable: ${project_dir}/mvnw" >&2
  exit 1
fi

export OPENCLAW_GATEWAY_URL="${OPENCLAW_GATEWAY_URL:-ws://127.0.0.1:18789}"

echo "Starting AI Dev OS backend on http://127.0.0.1:18080"
echo "OpenClaw gateway: ${OPENCLAW_GATEWAY_URL}"

exec "${project_dir}/mvnw" \
  -f "${project_dir}/pom.xml" \
  spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=18080
