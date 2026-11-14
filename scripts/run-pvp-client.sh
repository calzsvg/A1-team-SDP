#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
options=("-Dpvp.port=${2:-7777}")
if [[ -n "${1:-}" ]]; then options+=("-Dpvp.host=$1"); fi
exec java "${options[@]}" -cp 'bin:res' engine.Core
