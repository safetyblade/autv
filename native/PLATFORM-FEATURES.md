# AU TV platform integration

All integration is additive. The catalogue is read from the existing guide,
playlist and XMLTV endpoints. Existing Live Channels/TVirl configuration and
production generation remain authoritative and unchanged.

## One tuning and playback path

`ChannelTuning` resolves an editorial channel number or unique provider `tvgId`.
A known URL permits an attempt regardless of stale availability flags; missing
or ambiguous identities return to the guide. `PlaybackModel` owns one Media3
player for the primary activity and retains it across configuration changes.
Guide selection, CH buttons/keys, TV numeric entry and incoming intents all
call `PlaybackModel.request`. A repeated request for the playing channel does
not recreate its media item. An actual playback error still permits retry.
The last channel number is saved for process restoration; process death may
require reconnecting its stream. This is not a background audio service.

Examples (main app installed):

```sh
adb shell am start -a android.intent.action.VIEW -d 'autv://channel/419' -p dev.prestwich.autv
adb shell am start -a android.intent.action.VIEW -d 'autv://channel/id/PROVIDER_ID' -p dev.prestwich.autv
adb shell am start -n dev.prestwich.autv/.MainActivity --ei dev.prestwich.autv.CHANNEL_NUMBER 419
adb shell am start -n dev.prestwich.autv/.MainActivity --es dev.prestwich.autv.CHANNEL_ID PROVIDER_ID
```

Percent-encode the ID as one URI path segment. TV home cards use explicit
package/component intents containing the same `autv://channel/NUMBER` route.
`singleTask` delivers new tuning requests to the existing activity/player.
Unavailable or malformed requests open the guide without stopping another
playing channel. On TV, type a number while the guide is closed, then OK/Enter;
without OK it tunes after 1.5 seconds. Number keys in the guide remain available
for search. Phone search remains touch-first.

## Mobile PiP — implemented

Requires Android 8.0/API 26+, the advertised PiP feature and platform/user
permission. Android 7/API 24–25 and devices without PiP continue normal playback
without it. Android TV is deliberately excluded from automatic mobile entry.

When the player is actually playing, Home/user-leave requests PiP on API 26–30.
API 31+ uses `setAutoEnterEnabled`, a source rectangle and seamless resizing.
Inactive/failed playback does not enter PiP. The video view remains attached to
the same player; guide, branding, buttons and other chrome are removed immediately
while PiP is active. Returning restores the retained guide/player state. A
stopped full-screen activity pauses; an activity still in PiP continues playing.
The first version has no custom PiP channel actions.

Test on a phone/tablet:

1. Start a playable channel, optionally leave the guide open, then press Home.
2. Confirm a video-only PiP window, continuing picture/audio, with no AU TV
   guide, Cast button or channel panel in it.
3. Expand the window; confirm the same channel resumes without reconnecting
   unnecessarily and the previous guide state returns.
4. Rotate while playing; confirm the player/channel survives. Test a second
   direct link and CH change after returning.
5. Disable PiP for AU TV in Android app settings and repeat Home: playback must
   pause in the background and returning must restore the normal UI.
6. Repeat with gesture navigation on API 31+; the API 28 emulator only establishes
   the legacy Home-entry path. OEM transitions and task dismissal need hardware
   checks.

## Android TV home — implemented where the provider/launcher supports it

The existing Leanback launcher, AUTV banner and icon remain in place. On a TV
with API 26+, `TvHomePublisher` creates **one** owned `TYPE_PREVIEW` channel named
**AU TV Live**. It contains at most eight live catalogue items: the current/recent
channel plus a representative from each canonical genre. Guide-only channels
are omitted. Items include number/name, genre, live status, artwork and direct
tuning intents. Publication is refreshed after catalogue load and tuning;
programs are updated by owned identity, not repeatedly appended. Only this
package's row/items are touched. A missing/restricted TV provider is caught
and cannot prevent the guide or playback loading.

Android reviews channel visibility; `requestChannelBrowsable` requests the
first default preview row. Users/launchers can hide or reorder it. No claim is
made that every OEM home screen displays preview channels. On API 24–25 the app
still has its TV launcher but does not publish this API 26+ surface. Watch Next
is omitted: an indefinite live channel has no meaningful resume position and
would clutter the user's Watch Next list.

On a supported Android TV, open AU TV once and allow the catalogue to load.
Return Home, enable/reorder **AU TV Live** in Customize channels if necessary,
and select an item. Verify it tunes the advertised number directly and reuses
an already-running AU TV task. Check row count remains one after tuning/relaunch.
A debug device can inspect publisher status with:

```sh
adb shell run-as dev.prestwich.autv cat shared_prefs/tv-home.xml
```

A phone emulator lacks a TV provider/TV launcher; it cannot establish actual
home-row visibility. The implementation uses the framework TV provider APIs;
Kogan/OEM launcher presentation remains a device check.

## Android TV PiP — deferred

The retained player and chrome-suppression path are suitable for a later TV
PiP package. TV support is device/API dependent and differs from mobile PiP
(including newer TV expanded-PiP policy/use cases). This pass does not request
TV PiP or privileged/OEM permissions. The input probe reports the device's
advertised PiP feature; that feature alone does not prove permission or a usable
TV interaction. Mobile PiP is independent of this assessment.

## Isolated Kogan TV Input Framework probe — ready for device test

Build/install the **separate experimental APK**; the main APK contains no
`TvInputService`, no dependency on the probe, and no TV-input setup components:

```sh
gradle -p android :tif-probe:assembleDebug
adb install -r android/tif-probe/build/outputs/apk/debug/tif-probe-debug.apk
adb shell am start -n dev.prestwich.autv.tifprobe/.ProbeActivity
```

The probe uses the standard TV-input service action, metadata and a service
protected by `android.permission.BIND_TV_INPUT`. The permission protects system
binding; the application does **not** request that signature permission for
itself. Only the ordinary TV-provider write permission is requested. No root,
privileged installation, shell permission grants or OEM APIs are used.

The probe shares catalogue loading, `ChannelTuning` and the `AuTvPlayer` primitive.
A TIF session owns a system-supplied `Surface`, so this separate APK necessarily
has a session-local player; it never creates another player in the primary AU TV
process. Losing the system surface pauses the probe player; a released session
releases its engine. Failure/absence of TIF has zero effect on the primary app. It is not a
production adapter: only three sample channels, no programme publishing,
recording, parental-control integration, subtitle track catalogue or numeric
remote interception are provided.

### Required Kogan hardware checks

Record the model, Android/API version and current system Live TV app. Keep the
working TVirl/Live Channels inputs configured throughout these tests.

1. **Registration:** install/launch the probe and refresh its capability report.
   Does it show an input ID for `ProbeInputService`? Record Leanback/Live TV/PiP
   feature flags. An absent input means the device's framework did not register
   it; preserve the error/status before proceeding.
2. **Own-channel provider access:** select **Publish 3 owned test channels**.
   It should report up to three channels, or the exact provider/security failure.
   Only rows owned by `dev.prestwich.autv.tifprobe` are written. No existing
   TVirl/Live Channels channel rows are changed.
3. **Framework binding and tune:** select **Test sample in embedded system
   TvView**. Observe the diagnostic session/tune/channel/first-frame fields.
   This differentiates successful system binding from a network/codec failure.
4. **System visibility:** open the Kogan system Live TV/input interface. Look
   for **AU TV Experimental Input**, run its input setup if offered, choose
   **Publish 3 owned test channels** and **Finish input setup**, and enable the
   sample channels using that system app's channel-selection interface. Apps
   cannot arbitrarily mark legacy tuner channels browsable. Does the input
   appear and can one of the sample channels play through system Live TV?
5. **Physical TV button:** press the actual remote's TV button from Home and
   another app. Record which system activity/input opens. If Kogan settings
   offer a default input/TV-button target, test selecting the experimental
   input without removing the working TVirl input. Record whether the setting
   persists after restart. Successful embedded playback or input registration
   does **not** establish physical-button routing. This build does not intercept,
   remap or claim ownership of that button.
6. **Coexistence:** verify TVirl/Live Channels still tune the original playlist;
   then test the main AU TV APK with the probe absent. Uninstalling the probe
   removes the experiment; it is never needed for AU TV's guide, home row or PiP.

Useful evidence commands (do not grant additional permissions):

```sh
adb shell pm list features
adb shell dumpsys tv_input
adb shell dumpsys package dev.prestwich.autv.tifprobe
adb shell run-as dev.prestwich.autv.tifprobe cat shared_prefs/probe.xml
adb shell dumpsys activity activities
adb logcat -d -s TvInputManager TvInputService ExoPlayerImpl
```

A normal third-party declaration is permitted by the public framework contract.
Actual registration, TV-provider policy, system-app visibility and remote-button
routing are OEM decisions, and cannot be conclusively answered without the target
Kogan hardware. A missing provider on a phone emulator is not evidence that the
Kogan adapter is not viable.

## API evidence

The implementation was checked against the installed API 36 framework stubs
and AOSP source. Android developer documentation endpoints were blocked by this
cloud network's proxy; corresponding public AOSP source was accessible.

- [Android PiP guidance](https://developer.android.com/develop/ui/views/picture-in-picture)
- [Android TV preview channels](https://developer.android.com/training/tv/discovery/recommendations-channel)
- [TV Input Framework](https://developer.android.com/training/tv/tif)
- [Android TV PiP guidance](https://developer.android.com/training/tv/playback/picture-in-picture)
- [AOSP TvInputService](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/media/java/android/media/tv/TvInputService.java): public service contract and binding permission.
- [AOSP TvContract](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/media/java/android/media/tv/TvContract.java): preview programs and `requestChannelBrowsable` policy (first preview request approved by system policy).
- [AOSP TV provider permissions (API 28 mirror)](https://github.com/LineageOS/android_packages_providers_TvProvider/blob/lineage-16.0/AndroidManifest.xml): own-data write permission is normal; deprecated own-data read permission is no longer enforced. No all-EPG or watched-program privileged permission is requested.
- [AOSP Activity](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/Activity.java): PiP lifecycle/entry APIs.

## Validation assets

The non-exported debug-only `PlatformTestActivity` uses a synthetic silent 160×90 HLS
fixture generated with FFmpeg, independent of network providers. It exercises
the same activity, retained model and Media3 HLS player. It is not in release
builds and has no launcher entry. Instrumented tests cover real Home/PiP entry,
return/media identity, recreation, deep links, guide fallback and PiP chrome
suppression. Guide interaction tests also cover TV numeric tuning and the
existing Pass 3 interactions in portrait/landscape layouts. Actual GMS Cast
discovery and OEM TV surfaces require device testing; remote Cast media loading
remains outside this proof.

### Completed cloud checks

- Main and separate probe debug APKs build and verify their signatures.
- 16 JVM tests pass.
- Nine guide/chrome tests pass in portrait and landscape layouts.
- Three platform tests pass in each layout: Home/PiP and return without changing
  media/player identity, activity recreation and direct-link/guide fallback.
- The probe installs and launches on the API 28 phone emulator. It reports
  absent Live TV/Leanback and no registered input; requesting publication
  reports the missing input without crashing. This is an unsupported-phone
  result, not a finding about Kogan hardware.
- Main APK manifest contains no TV-input service. Protected production hashes
  remain unchanged; the native workflow remains manual-only.
