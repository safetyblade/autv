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

## Device smoke test

1. Launch AU TV with Internet access. It loads the existing published `guide.json`
   over HTTPS and sorts channels by number. A failed load offers Retry.
2. Use D-pad up/down and OK to choose a channel. Unavailable rows are labelled and
   disabled; missing or invalid stream URLs cannot block the rest of the guide.
3. Use channel up/down to tune, skipping unavailable channels and wrapping at the
   ends. Touch users have CH −, CH + and Guide buttons.
4. Use Guide/Menu to toggle the overlay, OK during playback to open it, and Back
   to close it. Test opening the guide again after scrolling.
5. Test an available HLS feed. Media3 uses an explicit HLS media type, including
   for extensionless provider URLs. Playback errors offer a message while other
   channels remain selectable. Playback pauses while the app is in the background.
6. On a sender-capable phone/tablet with Google Play services, open the Cast
   chooser. Its framework uses Google's Default Media Receiver (`CC1AD845`);
   no custom receiver is needed. Cast initialization failure does not block TV
   browsing or local playback.

The proof includes Cast discovery/session selection, not remote media loading or
phone-to-TV controller synchronization. Device playback, remote focus behaviour,
Cast discovery and provider geography/codec compatibility require device testing.
Full EPG, now/next, numeric tuning and genre browsing remain future work.

## Tests and boundaries

Six JVM tests cover mixed availability, missing and malformed rows/URLs, sorting,
counts, empty guides, channel skipping and wraparound. Availability counts are
derived from usable rows; there is deliberately no requirement that every guide
channel be playable. Guide loading uses connection/read timeouts and releases its
connection. The player module exposes the stable Media3 `Player` interface rather
than leaking its private ExoPlayer implementation onto the app's compile classpath.
