#!/usr/bin/env bash
# Fixture credentials only. The emulator already has app + instrumentation APKs installed.
set -euo pipefail
run_fixture() {
  local method="$1"
  local output
  output=$(adb shell am instrument -w -e class "dev.prestwich.autv.provider.ProviderUpgradeTest#$method" \
    dev.prestwich.autv.test/androidx.test.runner.AndroidJUnitRunner)
  printf '%s\n' "$output"
  # adb instrumentation does not reliably propagate JUnit failure as its exit status.
  [[ "$output" == *'OK (1 test)'* && "$output" != *'FAILURES'* ]]
}
run_fixture beforeUpdate
gradle -p android :app:assembleDebug -PautvVersionCode=2
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
run_fixture afterUpdate
