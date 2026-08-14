#!/usr/bin/env bash

set -euo pipefail

database_scripts_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${database_scripts_dir}/../../../.." && pwd)"
compose_file="${repository_root}/docker-compose.yml"
migrations_dir="${repository_root}/services/orchestrator/src/main/resources/db/migration"
postgres_database="${POSTGRES_DB:-ai_dev_os}"
postgres_user="${POSTGRES_USER:-ai_dev_os}"

fail() { echo "ERROR: $*" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"; }
compose() { docker compose -f "${compose_file}" "$@"; }

postgres_container() {
  local container
  container="$(compose ps -q postgres)"
  [[ -n "${container}" ]] || fail "PostgreSQL container is not running"
  printf '%s\n' "${container}"
}

require_postgres_healthy() {
  local container status
  container="$(postgres_container)"
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container}")"
  [[ "${status}" == "healthy" ]] || fail "PostgreSQL is not healthy: ${status}"
}

psql_value() {
  local database="$1" sql="$2"
  compose exec -T postgres psql -X -v ON_ERROR_STOP=1 -U "${postgres_user}" -d "${database}" -tAc "${sql}"
}

database_exists() {
  [[ "$1" =~ ^[A-Za-z0-9_-]+$ ]] || fail "Invalid database name"
  [[ "$(psql_value postgres "SELECT count(*) FROM pg_database WHERE datname='$1'")" == "1" ]]
}

application_migrations() {
  local migration
  for migration in "${migrations_dir}"/V*.sql; do
    [[ -f "${migration}" ]] || fail "No application migrations found"
    basename "${migration}"
  done | sort -V
}

database_migrations() {
  psql_value "$1" "SELECT version || ':' || name FROM schema_migrations ORDER BY version"
}

check_migration_compatibility() {
  local database="$1" require_complete="${2:-false}"
  local app_file version expected actual pending=0
  while IFS= read -r app_file; do
    version="${app_file#V}"; version="${version%%__*}"
    actual="$(psql_value "${database}" "SELECT COALESCE((SELECT name FROM schema_migrations WHERE version=${version}),'')")"
    if [[ -z "${actual}" ]]; then
      echo "PENDING migration ${app_file}"
      pending=$((pending + 1))
    elif [[ "${actual}" != "${app_file}" ]]; then
      fail "Migration name mismatch at V${version}: database=${actual}, application=${app_file}"
    fi
  done < <(application_migrations)

  while IFS=: read -r version actual; do
    [[ -n "${version}" ]] || continue
    expected="$(application_migrations | grep -E "^V${version}__" || true)"
    [[ -n "${expected}" ]] || fail "Database contains migration V${version} not known to this application"
  done < <(database_migrations "${database}")

  if [[ "${require_complete}" == "true" && "${pending}" -ne 0 ]]; then
    fail "Database has ${pending} pending application migration(s)"
  fi
  echo "Migration compatibility OK (pending=${pending})"
}

critical_counts_json() {
  psql_value "$1" "SELECT json_build_object(
    'repository_documents',(SELECT count(*) FROM repository_documents),
    'tasks',(SELECT count(*) FROM tasks),
    'audit_events',(SELECT count(*) FROM audit_events),
    'schema_migrations',(SELECT count(*) FROM schema_migrations))::text"
}
