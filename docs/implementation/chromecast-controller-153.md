# Chromecast controller and reliability — issue #153

## Investigation, 3 November 2026

Reviewed PR #125 (merged as 80e669b), `cast-media-handoff.md`, CastSender,
CastMediaResolver, CastTransfer, PlaybackModel and PlayerScreen before changing them.
PR #125 already correctly distinguished LOAD acknowledgement from matching PLAYING;
that correlation architecture, receiver app ID and session discovery remain intact.

**Pokémon's physical receiver failure is unresolved. This is not engineering closure
of CC-01, and no receiver incompatibility, geographic restriction or repair is proven.**
The owner observed local playback and a receiver timeout. That rules out a simple
claim that the channel is universally unavailable; it does not establish why the
receiver's independent requests fail. No stream or channel identity is replaced.

The attached safe HTTP evidence is `evidence/cast-153-source-probe.json`.
Two User-Agents were compared (AUTV probe and a handset-like Android/Media3 string;
the latter is a comparison request, not a capture of the owner's phone):

| Channel | Observed from this workspace | Unobserved |
|---|---|---|
| 200 Watch AEW | Tubi origin returns 302 to host `tubi.apollo.adrise.io` with both agents | Redirect destination blocked by workspace proxy; manifest, segments and receiver playback |
| 201 CW Presents WWE NXT | Supplied Roku host `aka-live1050.delivery.roku.com` | Workspace proxy denies connection before origin response; no origin status inferred |
| 202 TNA Wrestling | Actual playlist source is Plex; HTTP 200 HLS master for both agents; `avc1.4d4029`, `avc1.640029`, `mp4a.40.2`; no EXT-X-MAP or encryption tag observed in master | Variant/segment container, encryption in child, receiver video/audio |
| 1215 Pokémon | `jmp2.uk` returns 302 to `ipv4.pluto.tv` with both agents | Workspace proxy denies destination; effective terminal URL, manifest, child, segments, codecs, encryption, expiry and receiver retrieval |

Proxy restrictions are **engineering environment limitations**, not proof of provider
geoblocking. The comparison found no initial-redirect User-Agent difference for
Pokémon; dependencies farther downstream remain unknown. No signed URL, cookie or
query value is included in the evidence. No regional identity/IP or authorisation
headers were fabricated.

### Samsung AU alternate

`https://i.mjh.nz/SamsungTVPlus/au.m3u8` returns HTTP 404. The public
`https://i.mjh.nz/SamsungTVPlus/.channels.json.gz` catalogue returns HTTP 200;
its region keys are at, ca, ch, de, es, fr, gb, in, it, kr, us. There is no AU region.
Pokémon entries were found in CA, GB and US, which does not prove an Australian
stream or authorise reusing a foreign one. The current upstream repository's
SamsungTVPlus directory also has no AU listing. These are catalogue observations,
not a claim that Samsung cannot offer Pokémon in Australia through another route.
No verified publicly accessible, authorised AU alternate was established.

## Changes

- Explicit DISCONNECTED, CONNECTING, LOADING, BUFFERING, PLAYING, PAUSED, FAILED
  phases. LOAD acceptance remains a separate diagnostic event, not ownership.
- Preserved generation/media-session correlation and local playback until initial
  matching PLAYING. Local decoding stops once on transfer, not on every telemetry
  update. A confirmed retune retains the controller with a loading indication;
  failed loads/disconnect restore local playback through the existing tune path.
- Mobile remote ownership completely removes PlayerView from composition, including
  during remote buffering and guide browsing. The controller remains visible and
  respects safe drawing insets, scrolls in short/landscape viewports and has 48dp
  buttons. It includes AUTV, receiver/status, complete channel title/logo/number,
  now/next, timing/progress, LIVE, CH−/CH+, Guide, Settings and stop/local recovery.
- Pause, mute and volume appear only for receiver-advertised media commands.
  Retry casting and Play locally are available after failure. Guide errors render
  once as a separate message, never as programme metadata or a channel-unavailable
  flag. A retry clears the previous Cast notice.
- A shared 30-second watchdog preserves the original bounded recovery behavior.
  Repeated buffering cannot extend it; superseded work and confirmed media cancel it.
- Bounded manifest reading fixes partial InputStream reads. Public HLS diagnostics
  sample one variant and up to 1024 bytes of one segment, follow bounded safe
  redirects and report MIME, codec families, encryption method and container.
  Variant EXT-X-MAP can supply fMP4 hints while the receiver still gets the master
  URL. Child probe failure is evidence only and does not reject an accepted master.
  The app never tries to bypass encryption or export account-bound media.
- Logcat `AUTV.Cast` uses channel/ticket, phase, redirect count/host, numeric HTTP
  and SDK results, matched status, receiver errors and elapsed time. Raw media URLs,
  body content, customData, signed queries and receiver names are never logged.

## Validation and delivery

See the PR for exact commit, test totals, workflow/artifact and installable APK.
The existing AUTV Full Application Debug workflow builds `assembleFullAppDebug`
with `-PautvMediaLab=false`, verifies `dev.prestwich.autv` and phone/TV launcher,
and collects focused Android tests plus synthetic controller screenshots.
JVM coverage includes master/variant/segment parsing, retained signed query bytes,
redacted logs, child-probe failure, LOAD/PLAYING distinction, stale generations,
buffering, rejection, fallback/disconnect bookkeeping, and virtual-time 30-second
expiry/cancellation. Android tests use the real model/player/Compose/SDK envelope
with an explicitly fake Cast transport, including background/foreground, controller
visibility, controls, guide recovery, one timeout message and both orientations.
These tests do not establish receiver compatibility.

## Owner acceptance — pending, do not merge

Install **AUTV-Full-App-Debug.apk**, package **dev.prestwich.autv** (full app).
Keep the current installation/data if a debug signing/version mismatch prevents an
update; report that mismatch instead of uninstalling the working app.

On the same phone/Wi-Fi and Deck TV, run **200 → 201 → 202 → 1215 → 200**.
For each channel confirm TV video **and audio**, phone channel identity, programme
progress, receiver state, controls, guide access and no local black/frozen surface.
Check rejected LOAD, 30-second no-playback, buffering after confirmation, rapid
switches, retry, stop/local recovery, Wi-Fi interruption and background/foreground.
Verify normal phone and Android TV playback remain usable. These checks are pending.

Capture only the app's safe `AUTV.Cast` lines for each ticket. For Pokémon determine
whether failure is sender preflight, LOAD rejection, or no matching PLAYING after an
accepted LOAD. Collect numeric detailed receiver errors where available. A generic
receiver does not expose its actual HTTP requests or first decoded audio/video frame
to this sender; sender segment samples and PLAYING alone cannot prove physical AV.
If HTTP succeeds but playback never confirms, use an authorised receiver diagnostic
capture to resolve downstream regional/expiry/header/codec/encryption differences.
Only select an alternate after authorised AU accessibility and physical receiver AV
are demonstrated. CC-01 remains open until that evidence or a demonstrated repair.
