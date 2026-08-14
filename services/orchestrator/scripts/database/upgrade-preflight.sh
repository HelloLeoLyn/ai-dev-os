#!/usr/bin/env bash

set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/common.sh"

backup_dir=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-dir) backup_dir="${2:-}"; shift 2 ;;
    *) fail "Unknown preflight argument: $1" ;;
  esac
done
[[ -n "${backup_dir}" ]] || fail "--backup-dir is required"

require_postgres_healthy
database_exists "${postgres_database}" || fail "Database does not exist: ${postgres_database}"
check_migration_compatibility "${postgres_database}" false
echo "Pre-upgrade counts: $(critical_counts_json "${postgres_database}")"
"${script_dir}/backup-postgres.sh" --output-dir "${backup_dir}" --label pre-upgrade
echo "Upgrade preflight PASS"
