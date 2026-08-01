#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${script_dir}/.." && pwd)"
frontend_dir="${project_dir}/frontend"

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  echo "Node.js and npm are required but were not found in PATH." >&2
  exit 1
fi

if [[ ! -d "${frontend_dir}/node_modules" ]]; then
  echo "Frontend dependencies are missing. Run: cd frontend && npm install" >&2
  exit 1
fi

echo "Starting AI Dev OS frontend on http://127.0.0.1:15174"
echo "API proxy target: http://127.0.0.1:18080"

cd "${frontend_dir}"
exec npm run dev -- --host 127.0.0.1 --port 15174 --strictPort
