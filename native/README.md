# AU TV Native

This directory is an additive client layer for the existing AU TV service.

## Safety boundary

The existing service remains authoritative and untouched:

- `playlist.m3u` remains the Live Channels / TVirl playlist endpoint.
- `epg.xml.gz` remains the XMLTV endpoint.
- `channel_guide.csv` remains the editorial source of truth.
- the existing refresh workflow and source-selection scripts remain unchanged.

The native work consumes those outputs. It does not replace or refactor them.

## New client endpoint

`guide.json` is an application-facing index joining the active channel guide to the currently published playlist. Channels can remain in the intended guide with `available: false`; clients must degrade gracefully.

## Prototype target

Load guide → browse → play HLS → change channel → show guide → expose Cast on sender-capable Android devices.

The Android project lives at `/android`.

## Build the proof

Use JDK 17, Gradle 8.13, Android SDK platform 36 and build tools 35.0.0.
From the repository root:

```sh
gradle -p android :app:assembleDebug
gradle -p android :core-data:testDebugUnitTest :core-guide:testDebugUnitTest
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

The APK supports Android 7.0/API 24 and later, with both ordinary and Android TV
launchers. The manual-only `Native app` workflow runs the build and tests and
uploads `autv-debug`. No production refresh workflow or output is changed.

## Pass 2: controls and programme guide

The player screen has a navy/teal AU TV identity, a channel-logo area, now/next
programme titles and a dark channel drawer. Replace
`android/app/src/main/res/drawable/autv_logo.xml` with your own logo asset to
customize the branding without changing the UI structure.

The header holds Cast outside the guide drawer and within system safe-area
insets in both orientations. Phone controls remain below the drawer: CH −,
Guide/Close guide and CH +. Back closes the guide before leaving the app, and
handset users can tap the scrim outside the drawer to dismiss it. On TV,
D-pad navigation selects channel rows; Guide/Menu toggles the drawer and
channel keys tune directly. The Close button is initially focused on opening,
and the list scrolls to the selected channel.

`guide.json` defines the editorial lineup and numbers. `playlist.m3u` supplies
stream URLs, provider IDs and logos, reconciled by unique channel name or ID
rather than assuming that snapshot channel numbers agree. For example, Ink
Master is guide channel 419 but has a playlist stream under 418. The native
client preserves 419 and restores its stream and logo without changing either
production file.

- **Guide only:** no stream URL has been found.
- **Stream known:** a URL exists; playback can be attempted even if a stale
  guide availability flag says false.
- **Playback failed:** Media3 reported an actual error for that channel.
  Selecting it again retries; other channels remain usable.

`epg.xml.gz` loads separately from the lineup. XMLTV provider IDs and timezone
information determine now/next, refreshed on screen every 30 seconds. Missing,
expired or placeholder programme data displays a neutral fallback rather than
blocking playback. Logo image failures also have a local brand fallback.

Cast discovery/session selection uses Google's Default Media Receiver; remote
media loading and phone-to-TV controller synchronization remain future work.
Provider access, codecs and live Cast discovery need testing on the intended
hardware/network.

## Validation

```sh
gradle -p android :app:assembleDebug
gradle -p android :core-data:testDebugUnitTest :core-guide:testDebugUnitTest
gradle -p android :app:connectedDebugAndroidTest
```

JVM tests cover snapshot numbering drift, stream-known availability, malformed
rows/URLs, sorting, empty guides, navigation wraparound and XMLTV timezone and
placeholder handling. Compose instrumentation tests cover guide dismissal via
Close, toggle, Back and outside touch; CH buttons; channel/OK keys; Ink Master
selection; Cast/guide bounds; and portrait/landscape screenshots using deterministic
channel/EPG fixtures. Production files are never modified to run these tests.
