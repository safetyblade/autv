#!/usr/bin/env bash
# Set display metrics before starting instrumentation: Android caches display resources per process.
set +e
set -o pipefail
result=0
phase="${AUTV_SCREENSHOT_PHASE:-after}"
classes="${AUTV_SCREENSHOT_CLASSES:-dev.prestwich.autv.CastPlaybackTest,dev.prestwich.autv.ProviderPlaybackTest,dev.prestwich.autv.ProviderSettingsUiTest,dev.prestwich.autv.ProviderActivationTest,dev.prestwich.autv.ProviderQrIntegrationTest,dev.prestwich.autv.ProviderQrUiTest}"
mkdir -p android-ui-reports
trap 'adb shell wm size reset; adb shell wm density reset' EXIT
if [[ "$classes" != "dev.prestwich.autv.SettingsScreenshotsTest" ]]; then
  gradle -p android -PautvMediaLab=false :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class="$classes"
  if [[ $? -ne 0 ]]; then result=1; fi
  cp -r android/app/build/reports/androidTests/connected/debug android-ui-reports/regressions
fi
while read -r device size density; do
  adb shell wm size "$size"
  adb shell wm density "$density"
  gradle -p android -PautvMediaLab=false :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=dev.prestwich.autv.SettingsScreenshotsTest \
    -Pandroid.testInstrumentationRunnerArguments.autvScreenshotPhase="$phase" \
    -Pandroid.testInstrumentationRunnerArguments.autvScreenshotDevice="$device"
  if [[ $? -ne 0 ]]; then result=1; fi
  cp -r android/app/build/reports/androidTests/connected/debug "android-ui-reports/$device"
done <<'DEVICES'
phone 1080x1920 420
tv-720p 1280x720 160
tv-1080p 1920x1080 240
tv-4k 3840x2160 480
tv-720p-large-text 1280x720 160
DEVICES
# These shell-owned files survive AGP's test APK uninstall.
if ! adb pull /sdcard/Download/autv-settings-screenshots settings-screenshots; then result=1; fi
exit "$result"
