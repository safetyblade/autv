#!/usr/bin/env bash
# Keep instrumentation mandatory while preserving screenshots on test failures.
# This file runs in one shell; the emulator action executes separate script lines independently.
set +e
set -o pipefail
test_classes="${AUTV_SCREENSHOT_CLASSES:-dev.prestwich.autv.CastPlaybackTest,dev.prestwich.autv.ProviderPlaybackTest,dev.prestwich.autv.ProviderSettingsUiTest,dev.prestwich.autv.ProviderActivationTest,dev.prestwich.autv.ProviderQrIntegrationTest,dev.prestwich.autv.ProviderQrUiTest,dev.prestwich.autv.SettingsScreenshotsTest}"
gradle -p android -PautvMediaLab=false :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$test_classes" \
  -Pandroid.testInstrumentationRunnerArguments.autvScreenshotPhase="${AUTV_SCREENSHOT_PHASE:-after}"
result=$?
# The screenshot test exports shell-readable PNGs before AGP uninstalls the debug APK.
if adb pull /sdcard/Download/autv-settings-screenshots settings-screenshots; then
  echo "Collected synthetic settings and activation screenshots."
else
  echo "Screenshot collection failed; see the instrumentation report." >&2
  result=1
fi
exit "$result"
