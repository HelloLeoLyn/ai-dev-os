#!/usr/bin/env bash

set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/common.sh"

readiness_url="http://127.0.0.1:18080/api/health/readiness"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --readiness-url) readiness_url="${2:-}"; shift 2 ;;
    *) fail "Unknown post-upgrade argument: $1" ;;
  esac
done

require_command curl
require_postgres_healthy
check_migration_compatibility "${postgres_database}" true
readiness="$(curl --noproxy '*' -fsS --max-time 10 "${readiness_url}")" || fail "Readiness probe failed: ${readiness_url}"
grep -q '"status":"READY"' <<< "${readiness}" || fail "Application is not READY"
grep -q '"migrations":"complete"' <<< "${readiness}" || fail "Application migrations are not complete"
grep -q '"database":"up"' <<< "${readiness}" || fail "Application is not using a healthy PostgreSQL database"
echo "Post-upgrade counts: $(critical_counts_json "${postgres_database}")"
echo "Post-upgrade check PASS"
