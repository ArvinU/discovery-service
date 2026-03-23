#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== Discovery server =="
mvn -q -f "$ROOT/pom.xml" package

echo "== example-microservice =="
mvn -q -f "$ROOT/example-microservice/pom.xml" package

echo "== example-tcp-client =="
mvn -q -f "$ROOT/example-tcp-client/pom.xml" package

echo "== example-udp-client =="
mvn -q -f "$ROOT/example-udp-client/pom.xml" package

echo "== example-procman (npm) =="
cd "$ROOT/example-procman"
if [[ ! -d node_modules ]]; then npm install; fi
npm run build

echo "Done. JARs under each */target/ and ProcMan in example-procman/dist/"
