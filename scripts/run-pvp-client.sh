#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
exec java -cp 'bin:res' engine.Core
