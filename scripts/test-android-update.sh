#!/usr/bin/env bash
# Fixture credentials only. Gradle connected tests may uninstall both APKs during cleanup.
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
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
run_fixture beforeUpdate
gradle -p android :app:assembleDebug -PautvVersionCode=2
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
run_fixture afterUpdate
