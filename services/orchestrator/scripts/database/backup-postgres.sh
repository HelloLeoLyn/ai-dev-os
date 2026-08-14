#!/usr/bin/env bash

set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/common.sh"

output_dir=""
database="${postgres_database}"
label="manual"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) output_dir="${2:-}"; shift 2 ;;
    --database) database="${2:-}"; shift 2 ;;
    --label) label="${2:-}"; shift 2 ;;
    *) fail "Unknown backup argument: $1" ;;
  esac
done
[[ -n "${output_dir}" ]] || fail "--output-dir is required"
[[ "${database}" =~ ^[A-Za-z0-9_-]+$ ]] || fail "Invalid database name"
[[ "${label}" =~ ^[A-Za-z0-9._-]+$ ]] || fail "Invalid backup label"

require_command docker
require_command sha256sum
require_postgres_healthy
database_exists "${database}" || fail "Database does not exist: ${database}"
mkdir -p "${output_dir}"
output_dir="$(cd "${output_dir}" && pwd)"
if [[ "${output_dir}" == "${repository_root}" || "${output_dir}" == "${repository_root}/"* ]]; then
  fail "Backup output directory must be outside the Git repository"
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_id="ai-dev-os-${database}-${timestamp}-${label}"
dump_path="${output_dir}/${backup_id}.dump"
metadata_path="${output_dir}/${backup_id}.metadata.json"
[[ ! -e "${dump_path}" && ! -e "${metadata_path}" ]] || fail "Backup already exists: ${backup_id}"
tmp_dump="$(mktemp "${output_dir}/.${backup_id}.dump.XXXXXX")"
tmp_metadata="$(mktemp "${output_dir}/.${backup_id}.metadata.XXXXXX")"
cleanup() { rm -f "${tmp_dump}" "${tmp_metadata}"; }
trap cleanup EXIT

compose exec -T postgres pg_dump -U "${postgres_user}" -d "${database}" -Fc > "${tmp_dump}"
[[ -s "${tmp_dump}" ]] || fail "pg_dump produced an empty backup"
compose exec -T postgres pg_restore --list < "${tmp_dump}" >/dev/null

checksum="$(sha256sum "${tmp_dump}" | awk '{print $1}')"
size_bytes="$(stat -c '%s' "${tmp_dump}")"
server_version="$(psql_value "${database}" 'SHOW server_version')"
application_commit="$(git -C "${repository_root}" rev-parse HEAD 2>/dev/null || printf 'unknown')"
migrations="$(psql_value "${database}" "SELECT COALESCE(json_agg(json_build_object('version',version,'name',name) ORDER BY version),'[]'::json)::text FROM schema_migrations")"
counts="$(critical_counts_json "${database}")"

printf '{\n  "backupId": "%s",\n  "createdAtUtc": "%s",\n  "database": "%s",\n  "postgresVersion": "%s",\n  "applicationCommit": "%s",\n  "format": "pg_dump-custom",\n  "file": "%s",\n  "sizeBytes": %s,\n  "sha256": "%s",\n  "migrations": %s,\n  "criticalCounts": %s\n}\n' \
  "${backup_id}" "${timestamp}" "${database}" "${server_version}" "${application_commit}" \
  "$(basename "${dump_path}")" "${size_bytes}" "${checksum}" "${migrations}" "${counts}" > "${tmp_metadata}"

mv "${tmp_dump}" "${dump_path}"
mv "${tmp_metadata}" "${metadata_path}"
trap - EXIT
echo "Backup complete"
echo "Dump: ${dump_path}"
echo "Metadata: ${metadata_path}"
