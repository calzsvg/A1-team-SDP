#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p bin
find src -name '*.java' -print > bin/pvp-sources.txt
javac --release 17 -encoding UTF-8 -d bin @bin/pvp-sources.txt
echo 'Build complete (Java 17+).'
