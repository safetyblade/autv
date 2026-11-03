#!/usr/bin/env bash
# Keep instrumentation mandatory while preserving screenshots on test failures.
# This file runs in one shell; the emulator action executes separate script lines independently.
set +e
gradle -p android -PautvMediaLab=false :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.prestwich.autv.CastPlaybackTest,dev.prestwich.autv.ProviderPlaybackTest,dev.prestwich.autv.ProviderSettingsUiTest,dev.prestwich.autv.ProviderActivationTest,dev.prestwich.autv.ProviderQrIntegrationTest,dev.prestwich.autv.ProviderQrUiTest,dev.prestwich.autv.SettingsScreenshotsTest \
  -Pandroid.testInstrumentationRunnerArguments.autvScreenshotPhase=after
result=$?
if adb shell test -d /sdcard/Android/data/dev.prestwich.autv/files/settings-screenshots; then
  adb pull /sdcard/Android/data/dev.prestwich.autv/files/settings-screenshots settings-screenshots || {
    echo "Screenshot collection failed." >&2
    result=1
  }
else
  echo "Screenshot directory missing; inspect instrumentation failure." >&2
  result=1
fi
exit "$result"
