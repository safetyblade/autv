# Chromecast live playback

This change is confined to the native sender. Existing M3U/XMLTV endpoints,
source selection, generation scripts and production workflows are unchanged.
The native build workflow remains manual-only. No proxy or custom receiver is
used, and no TV-input or other platform experiment is involved.

## Playback path

`PlaybackModel` still owns the selected channel and the single local player.
Its guide, CH+/CH−, numeric and intent requests all call the same `tune` path.
A retained `CastSender` registers a `SessionManagerListener<CastSession>` with
the existing Cast context and Default Media Receiver (`CC1AD845`). Session
start/resume obtains `RemoteMediaClient` and loads the currently selected
channel; a connection established before guide loading is handled by the
normal initial tune when the catalogue arrives.

Every remote tune builds `MediaInfo` with the resolved HTTP(S) HLS URL as both
content ID and content URL, MIME type `application/x-mpegURL`, stream type
`LIVE`, channel number/name, current programme (or genre), logo and stable ID.
`RemoteMediaClient.load(MediaLoadRequestData)` requests autoplay without a VOD
seek position. Connection, load request, accepted load, actual receiver
PLAYING/PAUSED and failure are distinct states. A successful load response
alone does not prove receiver playback.

Initial handoff keeps local video until receiver status confirms playback
ownership. Then the local player is stopped, releasing decoding/buffering.
Further channel changes issue another remote load without starting the new
channel locally. Request generations reject results from superseded tunes;
status updates must match the requested content ID and unique load generation
in the media payload, including retunes that reuse the same stream URL. Returning to the app or
recreating the activity does not create another player or sender. Resuming an
existing session reuses already playing/paused matching media.

A rejected load, receiver media error or 30-second startup timeout reports a
Cast-specific diagnostic and restores the selected channel locally. It never
marks the channel globally unavailable. Retry by selecting the channel again,
or disconnect Cast to use local playback. The failure path requests remote
stop to avoid an old stream continuing alongside local fallback. Session
end/suspension also restores the selected channel; a background sender prepares
it paused until the app returns. Receiver playback is not paused merely because
the sender backgrounds. Mobile PiP remains a local-playback feature and is
disabled while connected to Cast.

## URL handling and limitations

Only the native Cast path removes query values containing unresolved IPTV
advertising macros such as `[IFA]`, `%5BUA%5D` or `{DEVICE_ID}`. Real query
parameters, including encoded tokens/signatures, are preserved byte-for-byte;
local Media3 always receives the original URL. Redirect resolution stays with
the receiver because provider routes can depend on its region and network.
No credentials, sender-only headers or cookies are forwarded.

The receiver needs direct access to manifests, variant/audio playlists,
segments and keys, suitable CORS responses and codecs supported by that
receiver. Region blocks, expiring URLs, account/cookie requirements, encryption
and provider-specific advertising are possible compatibility limits. Sender
HTTP checks and a working local player cannot prove a receiver can play a URL.
Actual Cast discovery, network loss and audio/video output require physical
hardware with Google Play services and a receiver on the same network.

## Representative URL checks

Run this read-only check from the repository root on a network permitted to
reach the providers (Python 3 and `ffprobe` required):

```sh
python native/check_cast_streams.py
```

It uses the real checked-in playlist, follows redirects, removes unresolved
macros, fetches a variant and a media-segment sample, records CORS and inspects
advertised/sample codecs. It does not write any service files. It is not a build
guard, and failures do not affect app availability.

The latest 2026-10-05 cloud check attempted these actual catalogue entries:

| Provider | Channel | Cloud result |
| --- | --- | --- |
| Pluto via published `jmp2.uk` redirect | Sky News | Proxy CONNECT denied (403); upstream not reached |
| Plex AU | USA TODAY | Master/variant HTTP 200, CORS `*`, H.264/AAC advertised; Amagi segment host proxy-blocked |
| Amagi / LG AU | 105 News24 Now | Master/variant HTTP 200, segment HTTP 206, CORS permitted; clear H.264 Main/AAC-LC sample decoded by ffprobe |
| Curated/direct | Hoop TV | Proxy CONNECT denied (403); upstream not reached |
| Custom / Tubi | Watch AEW | Proxy CONNECT denied (403); upstream not reached |

Initial checks were proxy-blocked for all five providers; later checks reached
Plex and Amagi. The Amagi result confirms a usable receiver-style HTTP/format
preflight for the exact macro-normalised URL. It does not prove audio/video on
a physical Cast receiver. Blocked requests are environment access failures,
not upstream compatibility verdicts. Initial provider hosts, the discovered
Plex segment host (`amg00731-gannettcoinc-usatodaynews-plex-r45dz.amagi.tv`) and
`streams2.sofast.tv` have been added to the cloud configuration draft for
review/save/publication. Other redirect/segment hosts may also need access.
The earlier PBS Kids direct sample disappeared from the published playlist
during concurrent main updates; the repeatable check now uses Hoop TV instead.
No representative channel is yet confirmed on a physical Cast receiver.

## Device acceptance and diagnostics

1. Install the main debug APK on a phone/tablet with Google Play services.
2. Play one of the channels above locally. Open the existing Cast picker and
   connect a video-capable receiver on the same network.
3. Verify the sender progresses through sending/starting to **Playing on
   [receiver]** and that video AND audio actually appear on the receiver.
   Local video must stop after confirmation. A mere connection or load accepted
   message does not pass this check.
4. Use CH+/CH− and choose a different guide channel. Confirm the receiver
   changes and the sender's local player remains stopped. Rapidly switch a few
   channels to check stale results do not report failure for the new channel.
5. Background/return to the sender and rotate it. Remote playback should
   continue, and no local PiP or duplicate audio should appear.
6. Disconnect using the Cast picker. Confirm the most recently selected channel
   resumes locally. Repeat with an unavailable/blocked provider and verify a
   Cast diagnostic appears while local fallback and other channels still work.
7. Record which of the five provider samples work on the target receiver, with
   receiver model, phone Android version, region and observed error codes.

Capture focused logs:

```sh
adb logcat -v time -s AUTV.Cast
```

Logs distinguish connection, requested channel/host/ticket, accepted load,
confirmed playback and load/receiver errors. Full stream URLs/tokens and custom
error payloads are omitted. The UI shows receiver state and actionable errors.
Cast status codes and receiver reason/type/detailed code, when supplied by the
SDK, identify load failures; timeout diagnostics point to internet/region/CORS/
codec checks. If a stream plays locally but fails on the receiver, record it as
a Cast limitation; do not edit the production playlist to hide it.

## Local validation

```sh
gradle -p android :app:assembleDebug
gradle -p android :core-data:testDebugUnitTest :core-guide:testDebugUnitTest
gradle -p android :app:connectedDebugAndroidTest
```

Unit tests cover URL preservation/macros, load-state transitions, late results,
remote retunes, failure/retry and disconnect invalidation. Instrumentation uses
the real SDK `MediaInfo` envelope and a debug-only deterministic Cast transport
to exercise the actual shared model/CH controls, handoff, local fallback,
disconnect recovery and local-PiP exclusion. These tests do not pretend to be
a physical receiver. Existing guide and local lifecycle/PiP tests also run.

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

Verified locally on 2026-10-05: the exact `assembleDebug` command passed;
26 JVM tests passed; all 15 instrumentation tests passed on the API 28 mobile
emulator, with the three Cast tests also passing after the final SDK metadata
and URL updates. APK signature verification passed. The Cast transport tests
use deterministic events; no physical receiver/provider compatibility is
claimed. All 20 tracked files outside the native/additive boundary were
verified unchanged by this change, and the workflow trigger was verified to be
`workflow_dispatch` only.
