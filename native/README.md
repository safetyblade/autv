# AU TV Native

This directory is an additive client layer for the existing AU TV service.

## Safety boundary

The existing service remains authoritative:

- `playlist.m3u` remains the Live Channels / TVirl playlist endpoint.
- `epg.xml.gz` remains the XMLTV endpoint.
- `channel_guide.csv` remains the editorial source of truth.
- the existing refresh workflow and source-selection scripts remain unchanged.

The native work consumes those outputs. It does not replace or refactor them.

## New client endpoint

`guide.json` is an application-facing index joining the active channel guide to the currently published playlist. Channels can remain in the intended guide with `available: false`; clients must degrade gracefully.

## Startup and last-known-good guide

`guide.json` alone is sufficient to display the catalogue. Startup reads the
private `filesDir/channel-guide-v1.json` cache without waiting for network access,
then refreshes JSON in the background. A successfully validated, non-empty remote
snapshot atomically replaces the cache; malformed/duplicate/empty snapshots leave
the last good file intact. Channel stream availability is not a validity guard.

The model exposes `LOADING`, `CACHED`, `READY` and `ERROR` startup states.
The cache read has a two-second UI deadline and JSON refresh has a thirty-second
UI deadline. Without a usable cache, remote failure leads to the existing Retry
state. With a cache, failure leaves the cached guide usable. A timed-out blocking
network worker cannot delay publication of the error/fallback state or apply a
late result.

Playlist reconciliation runs only after JSON has been published. Its failure
cannot hide the guide. Metadata updates keep the retained Media3 player and do
not reset search, category, scroll, overlay state or restart unchanged media.
Fresh catalogues replace old channel membership, so withdrawn channels disappear.

`native/tools/build_guide.py` validates the complete CSV (field counts, required
values and unique channel numbers) before atomically regenerating JSON exclusively
from Active rows. It never reads/merges the old generated JSON. The existing
refresh workflow already invokes this generator and fails before publication if
validation fails. In this cleanup, unverified 296/297 were withdrawn from the
native guide and playlist; no replacement source was introduced.

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
uploads `autv-debug`. The production refresh architecture is not changed.

## Download APKs on a PC

Use GitHub-hosted downloads rather than chat/workspace artifact links:

1. Sign in to GitHub and open [Native app builds](https://github.com/safetyblade/autv/actions/workflows/native-app.yml).
2. Choose **Run workflow**, select the branch containing the native changes,
   then run it. This workflow remains manual-only.
3. Open the completed run. Under **Artifacts**, download `autv-debug` for the
   normal AU TV app.
4. Extract the downloaded ZIPs on the PC. Copy `app-debug.apk` onto the USB drive. Install the main app first.

Downloads require a signed-in GitHub account with access
to the repository. Artifacts request 90-day retention (repository/organization
limits may shorten it); rerun the workflow when they expire. Each run builds the
selected branch, so publish the local native commits before requesting a build.
GitHub Releases are a future option for permanent, versioned downloads; this
workflow does not publish releases or modify production endpoints.

## Pass 3: playback chrome and navigable guide

The player uses near-black/navy video chrome, smoked surfaces and restrained
champagne/silver focus accents. Replace
`android/app/src/main/res/drawable/autv_logo.xml` with a final AUTV logo asset
without changing the branding area.

Controls appear at playback start and fade after 3.5 seconds without input.
Touch or D-pad centre/OK restores them; hidden playback has no persistent
buttons, panels or system bars. Opening the guide pins the chrome. Phone Cast stays
in the safe-area header above the drawer in both orientations. CH − and CH +
are available on screen; hardware channel keys tune even while controls are
hidden. Back closes the guide, then hides visible chrome, before normal Android
exit. Close, the guide toggle and handset
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

The TV guide uses the same dark card/chip design in a larger centred overlay;
there is no permanent rail, grid or bottom control bar. Remote focus has a
strong outline and raised contrast. Left/right browses category chips; down
enters the channel list; up from its first playable row returns to categories.
OK/Enter activates the focused control, and closing the guide returns focus to
the transient Guide button. The player view blocks native descendant focus so
the video surface cannot take focus behind an overlay.

Phone landscape uses 54% of the available content width, a combined compact
search/Close header, shorter category chips and denser rows with concise
programme metadata. Guide-open bottom controls are removed to free list space.
Portrait retains its larger search field and programme cards.

`PlaybackInteractionTest` dispatches remote keys through the real activity and
checks the retained player's media ID, URL and playing state for guide tuning,
CH keys/buttons and direct selection. A phone touch test checks actual media
replacement too. Debug-only fixture activities use distinct HLS asset URLs;
they never replace the production runtime catalogue.

Cast uses Google's Default Media Receiver and loads the tuned live HLS channel
through the retained shared player state. CH keys, guide selection and direct
tuning retune the receiver. Confirmed receiver playback stops local decoding;
disconnect or a failed remote load restores local playback. See
[CAST-PLAYBACK.md](CAST-PLAYBACK.md) for diagnostics, stream checks and the
remaining physical-receiver validation.

## Validation

```sh
gradle -p android :app:assembleDebug
gradle -p android :core-data:testDebugUnitTest :core-guide:testDebugUnitTest
gradle -p android :app:connectedDebugAndroidTest
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s native/tools -p "test_*.py"
```

JVM startup tests cover cache-first publication, remote success/failure/timeout,
playlist failure after JSON publication, cache integrity and replacement.
`GuideRefreshIntegrationTest` checks real model/player/media retention across a
cached-to-remote metadata refresh on an Android device. Generator tests cover
malformed CSV, duplicates, missing fields and withdrawal despite stale playlists.

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
player/direct-tuning contract and the TV home preview row. Android TV PiP and
Watch Next remain deferred.
