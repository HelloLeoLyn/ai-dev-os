#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backend_pid=""
frontend_pid=""

stop_services() {
  trap - EXIT INT TERM

  if [[ -n "${frontend_pid}" ]] && kill -0 "${frontend_pid}" 2>/dev/null; then
    kill "${frontend_pid}" 2>/dev/null || true
  fi
  if [[ -n "${backend_pid}" ]] && kill -0 "${backend_pid}" 2>/dev/null; then
    kill "${backend_pid}" 2>/dev/null || true
  fi

  wait "${frontend_pid}" 2>/dev/null || true
  wait "${backend_pid}" 2>/dev/null || true
}

handle_signal() {
  stop_services
  exit 130
}

trap stop_services EXIT
trap handle_signal INT TERM

"${script_dir}/start-backend.sh" &
backend_pid=$!

"${script_dir}/start-frontend.sh" &
frontend_pid=$!

echo "AI Dev OS development environment is starting."
echo "Frontend: http://127.0.0.1:15174"
echo "Backend:  http://127.0.0.1:18080"
echo "Press Ctrl+C to stop both services."

set +e
wait -n "${backend_pid}" "${frontend_pid}"
exit_status=$?
set -e

echo "A development service exited with status ${exit_status}; stopping the other service." >&2
exit "${exit_status}"
