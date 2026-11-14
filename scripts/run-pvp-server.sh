#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec java -cp bin pvp.PvpServer "${1:-7777}" "${2:-0.0.0.0}"
