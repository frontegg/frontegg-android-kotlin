#!/usr/bin/env bash
# android-emulator-runner runs each *line* of `with.script` as a separate `sh -c` invocation.
# Multi-line if/for must live in this file (see lib/script-parser.js in ReactiveCircus/android-emulator-runner).
set -euo pipefail

CLASS="${E2E_CLASS:?E2E_CLASS is required}"
METHODS="${E2E_METHODS:-}"

# One Gradle + one install per shard (not one per method) — faster and avoids flakiness between runs.
if [[ -n "$METHODS" ]]; then
  specs=""
  _old_ifs=$IFS
  IFS=,
  for m in $METHODS; do
    IFS=$_old_ifs
    [[ -z "$m" ]] && continue
    [[ -n "$specs" ]] && specs+=","
    specs+="${CLASS}#${m}"
  done
  IFS=$_old_ifs
  if [[ -z "$specs" ]]; then
    echo "::error::E2E_METHODS was non-empty but no method names parsed: '$METHODS'" >&2
    exit 1
  fi
  echo "::notice::connectedDebugAndroidTest — $specs"
  # timeout_msec lives in embedded/build.gradle only; duplicating it here breaks AGP (duplicate map key).
  ./gradlew :embedded:connectedDebugAndroidTest --no-daemon \
    -Pandroid.testInstrumentationRunnerArguments.class="${specs}"
else
  echo "::notice::connectedDebugAndroidTest — full class $CLASS"
  ./gradlew :embedded:connectedDebugAndroidTest --no-daemon \
    -Pandroid.testInstrumentationRunnerArguments.class="$CLASS"
fi
