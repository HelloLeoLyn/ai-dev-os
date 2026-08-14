#!/usr/bin/env bash

set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/common.sh"

require_command docker
volume_name="ai-dev-os_postgres-data"
if docker volume inspect "${volume_name}" >/dev/null 2>&1; then
  echo "PostgreSQL external volume already exists: ${volume_name}"
else
  docker volume create "${volume_name}" >/dev/null
  echo "Created PostgreSQL external volume: ${volume_name}"
fi
