#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
classes="$(mktemp -d)"
trap 'rm -rf -- "$classes"' EXIT
javac --release 17 -d "$classes" \
  "$repo/app/src/main/java/fun/cadacrew/roadledger/V71HomeStats.java" \
  "$repo/app/src/test/java/fun/cadacrew/roadledger/V71HomeStatsSelfTest.java"
java -cp "$classes" fun.cadacrew.roadledger.V71HomeStatsSelfTest
