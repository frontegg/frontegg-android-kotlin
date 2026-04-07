#!/usr/bin/env bash
# android-emulator-runner runs each *line* of `with.script` as a separate `sh -c` invocation.
# Multi-line if/for must live in this file (see lib/script-parser.js in ReactiveCircus/android-emulator-runner).
set -uo pipefail

CLASS="${E2E_CLASS:?E2E_CLASS is required}"
METHODS="${E2E_METHODS:-}"
MAX_ATTEMPTS="${E2E_MAX_ATTEMPTS:-3}"

# Build the test spec argument
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
  TEST_SPEC="$specs"
else
  TEST_SPEC="$CLASS"
fi

echo "::notice::connectedDebugAndroidTest — $TEST_SPEC"

# Retry loop: up to MAX_ATTEMPTS attempts on failure (handles emulator process crashes,
# WebView rendering glitches, transient Custom Tab issues). Between attempts, restart
# the demo app to clear state.
attempt=1
while [[ $attempt -le $MAX_ATTEMPTS ]]; do
  echo "::group::Test attempt $attempt/$MAX_ATTEMPTS"
  if ./gradlew :embedded:connectedDebugAndroidTest --no-daemon \
       -Pandroid.testInstrumentationRunnerArguments.class="$TEST_SPEC"; then
    echo "::endgroup::"
    echo "::notice::Tests passed on attempt $attempt"
    exit 0
  fi
  rc=$?
  echo "::endgroup::"
  echo "::warning::Attempt $attempt/$MAX_ATTEMPTS failed (exit $rc)"

  if [[ $attempt -lt $MAX_ATTEMPTS ]]; then
    echo "::group::Recover before retry"
    # Stop demo app, dismiss browser, return to home — clears stale UI state
    adb shell am force-stop com.frontegg.demo 2>/dev/null || true
    adb shell am force-stop com.android.chrome 2>/dev/null || true
    adb shell input keyevent KEYCODE_HOME 2>/dev/null || true
    # Ensure animations stay disabled in case the OS reset them
    adb shell settings put global window_animation_scale 0 2>/dev/null || true
    adb shell settings put global transition_animation_scale 0 2>/dev/null || true
    adb shell settings put global animator_duration_scale 0 2>/dev/null || true
    # Wait for system to settle
    sleep 5
    # Verify emulator is still alive
    if ! adb shell getprop sys.boot_completed 2>/dev/null | grep -q 1; then
      echo "::error::Emulator died after attempt $attempt — cannot retry"
      exit $rc
    fi
    echo "::endgroup::"
  fi
  attempt=$((attempt + 1))
done

echo "::error::Tests failed after $MAX_ATTEMPTS attempts"
exit 1
