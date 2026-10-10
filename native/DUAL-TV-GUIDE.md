# Android TV dual guide

The TV player retains its existing Media3 instance while either guide is open.
The handset guide is unchanged.

- **Guide/Menu:** open Quick Guide, then switch Quick Guide ↔ Full EPG.
- **Up/Down:** move between channels. Up from the first channel enters categories;
  Up again enters the Full EPG, Search and Close controls.
- **Left/Right:** browse categories/controls, or programmes in Full EPG.
- **OK:** tune the focused Quick Guide channel. Full EPG opens programme details;
  an airing programme offers **Watch live**. Future/past programmes offer information
  only. Rows with no schedule can still tune their known live stream.
- **Back:** close details, return from Full EPG to Quick Guide, then dismiss Quick Guide.
- **Page Up/Down or L1/R1:** change category without tuning.
- **Digits:** use the existing channel-number entry; OK commits immediately or the
  existing 1.5-second timeout commits. Existing CH +/- shortcuts are preserved.

Quick Guide opens on the playing channel and shows seven rows in a translucent
78%-width panel. Playing and browsing focus have separate indicators. Search is
an optional header action. The full guide has a fixed channel column and a
three-hour timeline; programme widths and gaps use actual XMLTV times. Selecting
another programme shifts the visible time window when needed. Returning to Quick
Guide retains its channel/category and lazy-list position.

The app continues to consume `guide.json` (the existing CSV-derived catalogue),
`playlist.m3u` and `epg.xml.gz`. It does not introduce another catalogue. EPG ID
and unique-name resolution are unchanged. Times display in the device's local
zone, including Australian timezone/DST settings. Programme descriptions come
from XMLTV, and no titles, schedules, recording or reminder features are invented.

EPG cache `programme-guide-v1.xml.gz` is read on an IO worker before refreshing.
Validated non-empty schedules atomically replace it; malformed, empty or failed
refreshes retain the last good schedule. Refresh runs every 30 minutes without
calling a player or tuning API. Expired data is not presented as currently airing.

## Validation and remaining device checks

Existing commands:

```sh
gradle -p android :app:assembleDebug
gradle -p android :core-data:testDebugUnitTest :core-guide:testDebugUnitTest
gradle -p android :app:connectedDebugAndroidTest
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s native/tools -p 'test_*.py'
```

In this workspace Gradle cannot initialize its local socket service (`Could not
determine a usable wildcard IP`), including the unmodified baseline. Source and
JVM tests can be checked with the cached Kotlin compiler/JUnit; this does not
substitute for APK packaging, Android instrumentation or hardware validation.
The existing manual-only Native app workflow can build `autv-debug` from
`feature/dual-tv-guide`; no playlist workflow changes are needed.

`DualGuideInteractionTest` uses synthetic fixtures for channels 200, 201, 202,
203 and 297, plus 651 rows. It checks focus, explicit selection, key repeats,
number entry, categories, virtualized scrolling, refreshed EPG focus, future/live
programme details and the Back sequence. Existing retained-player integration
coverage remains available. The channel 297 fixture does not reintroduce or
change any editorial channel. Neither channel 203 nor 297 playback faults are
fixed or validated by these guide tests.

Physical Android TV/Google TV focus, remote models, 600+ channel scrolling,
300 ms open / 100 ms focus / 60 fps targets and real-stream playback continuity
remain unverified until device testing. No device testing is claimed.
