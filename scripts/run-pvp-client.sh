#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec java "-Dpvp.host=${1:-127.0.0.1}" "-Dpvp.port=${2:-7777}" -cp 'bin:res' engine.Core
