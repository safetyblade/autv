#!/usr/bin/env bash
# Keep instrumentation mandatory while preserving screenshots on test failures.
# This file runs in one shell; the emulator action executes separate script lines independently.
set +e
set -o pipefail
gradle -p android -PautvMediaLab=false :app:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.prestwich.autv.CastPlaybackTest,dev.prestwich.autv.ProviderPlaybackTest,dev.prestwich.autv.ProviderSettingsUiTest,dev.prestwich.autv.ProviderActivationTest,dev.prestwich.autv.ProviderQrIntegrationTest,dev.prestwich.autv.ProviderQrUiTest,dev.prestwich.autv.SettingsScreenshotsTest,dev.prestwich.autv.AustralianProviderIntegrationTest,dev.prestwich.autv.provider.AustralianConfigurationIsolationTest,dev.prestwich.autv.provider.EncryptedProviderSessionStoreTest \
  -Pandroid.testInstrumentationRunnerArguments.autvScreenshotPhase=after
result=$?
# Debug APK: extract app-internal screenshots without Android/data scoped-storage assumptions.
# run-as requires this APK to remain debuggable; the command fails closed otherwise.
if adb exec-out run-as dev.prestwich.autv tar -C files -cf - settings-screenshots | tar -xf -; then
  echo "Collected synthetic settings and activation screenshots."
else
  echo "Screenshot collection failed; see the instrumentation report." >&2
  result=1
fi
exit "$result"
