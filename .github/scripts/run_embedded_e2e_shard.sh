#!/usr/bin/env bash
# android-emulator-runner runs each *line* of `with.script` as a separate `sh -c` invocation.
# Multi-line if/for must live in this file (see lib/script-parser.js in ReactiveCircus/android-emulator-runner).
set -euo pipefail

CLASS="${E2E_CLASS:?E2E_CLASS is required}"
METHODS="${E2E_METHODS:-}"

if [[ -n "$METHODS" ]]; then
  _old_ifs=$IFS
  IFS=,
  for m in $METHODS; do
    IFS=$_old_ifs
    [[ -z "$m" ]] && continue
    ./gradlew :embedded:connectedDebugAndroidTest --no-daemon \
      -Pandroid.testInstrumentationRunnerArguments.class="${CLASS}#${m}"
  done
  IFS=$_old_ifs
else
  ./gradlew :embedded:connectedDebugAndroidTest --no-daemon \
    -Pandroid.testInstrumentationRunnerArguments.class="$CLASS"
fi
