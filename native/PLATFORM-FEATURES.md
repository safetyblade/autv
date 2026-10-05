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
TV PiP or privileged/OEM permissions. Mobile PiP is independent of this deferred TV assessment.

