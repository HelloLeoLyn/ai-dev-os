#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "${AI_DEV_OS_POSTGRES_PASSWORD:-}" ]]; then
  echo "AI_DEV_OS_POSTGRES_PASSWORD must be set before starting the PostgreSQL backend." >&2
  exit 1
fi

export AI_DEV_OS_PERSISTENCE_TYPE=postgresql
export AI_DEV_OS_POSTGRES_URL=jdbc:postgresql://127.0.0.1:5432/ai_dev_os
export AI_DEV_OS_POSTGRES_USER="${AI_DEV_OS_POSTGRES_USER:-ai_dev_os}"

exec "${script_dir}/start-backend.sh"
