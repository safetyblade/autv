# AU TV native guide and supporting tooling

The native guide consumes `channel_guide.csv`, `playlist.m3u` and `guide.json`. The Android application is under `android/`, and app-side ABC/Network 10 direct channels are separately listed in `AustralianLiveChannels.kt`.

## Current operation

- `native/tools/build_guide.py` builds the native guide from approved curated data.
- The Android player retains a last-known-good guide snapshot and refreshes sources independently from channel identity.
- Channel availability, EPG availability and permitted playback are **different checks**. A stale region, signed redirect or failed playback result is not a permanent ban on later source recovery.
- Production updates require signed main-package APKs; independent test builds use the Trial suffix. See [Android update instructions](../docs/android-updates.md).
- The six owner-tested ABC/Network 10 channels play without login. 9Now, Seven and SBS remain account-linked services where supported.

For current source standards see [source discovery](../docs/SOURCE_DISCOVERY.md). Deep historical trials belong in dated GitHub issues or Git history rather than being repeated as release blockers.
