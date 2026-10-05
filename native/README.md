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

## Pass 3: playback chrome and navigable guide

The player uses near-black/navy video chrome, smoked surfaces and restrained
champagne/silver focus accents. Replace
`android/app/src/main/res/drawable/autv_logo.xml` with a final AUTV logo asset
without changing the branding area.

Controls appear at playback start and fade after 3.5 seconds without input.
Touch or D-pad centre/OK restores them; hidden playback has no persistent
buttons, panels or system bars. Opening the guide pins the chrome. Cast stays
in the safe-area header above the drawer in both orientations. CH − and CH +
are available on screen; hardware channel keys tune even while controls are
hidden. Back closes the guide first. Close, the guide toggle and handset
outside touch also dismiss it.

The guide opens scrolled and focused at the playing channel. It retains the
chosen category if that category contains the channel, otherwise switches to
its canonical genre. All and the eleven canonical genre filters support touch
and D-pad navigation; Page Up/Down or controller shoulder buttons skip between
categories. Search matches channel number, name and current programme title.
Rows prioritize logos, channel numbers, current programme times/progress and
next programme, with a restrained genre fallback when EPG is missing.

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
information determine now/next, refreshed on screen every 15 seconds. A unique
XMLTV display-name match provides a safe fallback when IDs differ; ambiguous
name matches are ignored. Missing,
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

JVM tests also cover canonical category filtering, programme-title search,
category retention and ID/name EPG resolution. They cover snapshot numbering drift, stream-known availability, malformed
rows/URLs, sorting, empty guides, navigation wraparound and XMLTV timezone and
placeholder handling. Compose instrumentation tests cover guide dismissal via
Close, toggle, Back and outside touch; auto-hide and touch/OK wake-up; hidden
channel keys and CH buttons; current-channel focus and category skipping;
number/name/programme search; channel logos/progress and Cast/guide bounds; and portrait/landscape screenshots using deterministic
channel/EPG fixtures. Production files are never modified to run these tests.

## Platform additions

See [PLATFORM-FEATURES.md](PLATFORM-FEATURES.md) for mobile PiP, the retained
player/direct-tuning contract, the TV home preview row and the **separate**
Kogan TV-input diagnostic APK, including exact device checks. The production
APK does not depend on that experiment. Android TV PiP and Watch Next remain
deferred.
