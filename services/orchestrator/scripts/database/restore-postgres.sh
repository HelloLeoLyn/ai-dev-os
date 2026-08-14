#!/usr/bin/env bash

set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/common.sh"

backup=""
metadata=""
target_database="${postgres_database}"
confirmed_database=""
safety_backup_dir=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup) backup="${2:-}"; shift 2 ;;
    --metadata) metadata="${2:-}"; shift 2 ;;
    --target-database) target_database="${2:-}"; shift 2 ;;
    --confirm-database) confirmed_database="${2:-}"; shift 2 ;;
    --safety-backup-dir) safety_backup_dir="${2:-}"; shift 2 ;;
    *) fail "Unknown restore argument: $1" ;;
  esac
done
[[ -f "${backup}" ]] || fail "--backup must reference an existing dump"
[[ -n "${metadata}" ]] || metadata="${backup%.dump}.metadata.json"
[[ -f "${metadata}" ]] || fail "Backup metadata not found: ${metadata}"
[[ "${target_database}" =~ ^[A-Za-z0-9_-]+$ ]] || fail "Invalid target database name"
[[ "${confirmed_database}" == "${target_database}" ]] || fail "--confirm-database must exactly match ${target_database}"
[[ -n "${safety_backup_dir}" ]] || fail "--safety-backup-dir is required"

require_command docker
require_command sha256sum
require_command curl
require_postgres_healthy
database_exists "${target_database}" || fail "Target database does not exist: ${target_database}"

expected_checksum="$(sed -n 's/.*"sha256": "\([0-9a-f]\{64\}\)".*/\1/p' "${metadata}")"
[[ -n "${expected_checksum}" ]] || fail "Backup metadata does not contain a valid SHA-256"
actual_checksum="$(sha256sum "${backup}" | awk '{print $1}')"
[[ "${actual_checksum}" == "${expected_checksum}" ]] || fail "Backup checksum mismatch"
compose exec -T postgres pg_restore --list < "${backup}" >/dev/null || fail "Backup archive validation failed"

if [[ "${target_database}" == "${postgres_database}" ]] && curl --noproxy '*' -fsS --max-time 1 http://127.0.0.1:18080/api/health >/dev/null 2>&1; then
  fail "Local Orchestrator is running; stop it before restoring ${target_database}"
fi

"${script_dir}/backup-postgres.sh" --output-dir "${safety_backup_dir}" \
  --database "${target_database}" --label pre-restore-safety

echo "Restoring ${target_database} from verified backup"
compose exec -T postgres pg_restore -U "${postgres_user}" -d "${target_database}" \
  --clean --if-exists --no-owner --no-privileges < "${backup}"
check_migration_compatibility "${target_database}" false
echo "Data integrity counts: $(critical_counts_json "${target_database}")"
echo "Restore complete: ${target_database}"
