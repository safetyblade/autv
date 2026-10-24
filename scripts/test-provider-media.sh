#!/usr/bin/env bash
# Only fixed sample labels/counters are exported; no URL/token/session dumps.
set -uo pipefail
trap 'adb logcat -d -s AUTV_MEDIA_QA:I "*:S" | tee media-sample-results.txt || true' EXIT
gradle -p android :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.prestwich.autv.PublicMediaCapabilityTest
