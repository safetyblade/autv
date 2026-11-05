# AUTV ABC / Network 10 native connection trials — 5 November 2026

## Actual protocol findings

In the GitHub Actions unregistered real-service probe run 41958319938, ABC's public live catalogue returned HTTP 200. More importantly, AUTV sent `productId=autv` and a newly generated device ID to ABC's first-party `/latest/ctv/link` endpoint **without borrowing a client API key**, and received HTTP 200 with a real four-character code, an expiry and request ID. Its immediate approval poll returned HTTP 404 (not yet approved). **No account was approved and no ABC video played in that run.**

The same probe requested Network 10's `/api/v1/config` with no registered signer and received HTTP 404. A Ten activation challenge was therefore not issued. This is a specific unresolved Network 10 input, not proof it is technically impossible.

## What this PR builds into AUTV

**ABC iview** — In debug AUTV, `Settings → Australian services → ABC iview → Connect ABC iview` invokes a genuine first-party AUTV-identified device-code request and the existing ABC polling, token persistence and native channel discovery. The response is displayed in AUTV's own QR/code screen. The experimental ABC account/device store has its **own encrypted namespace**, separate from approved ABC distributions. No profile key, signing secret, external app or borrowed identity is used. If the user approves the challenge, AUTV will attempt its real token and channel discovery. Success still does not establish media-authorisation rights.

**Network 10** — `Settings → Australian services → 10 → Try real 10 account linking` opens the existing debug-only `BroadcasterLinkLabActivity` inside AUTV. It attempts the real first-party config, generate-code and approval URLs if the server actually supplies them. It reports HTTP failure stages and never creates a fabricated activation code. The separate **Check 10 connection service** probe performs a read-only configuration request from the TV's own Australian network.

**Live playback trial** — The 10 settings page also provides **Play 10 VIC native trial**, using the same AUTV Media3 player and TV remote, with the previously isolated Media Lab's *unofficial* VIC-only HLS candidate. It is not a production-licensed channel and does not represent account linking, media rights, official DAI/IMA entitlement or verified ads. Only an explicit debug user action can start it.

**ABC catalogue diagnostics** — The ABC page provides **Check ABC live catalogue** and reports the actual channel names when the public API responds; missing metadata or network errors are displayed as specific diagnostic results.

Existing SBS, Nine and Seven adapters, guide favourites/IDs, DRM, advertisements and production source configurations are unchanged. The experimental live stream is never published to the channel guide or watched history. A release APK does not expose the trial controls or the unregistered ABC adapter.

## How to test

1. Install the full AUTV debug APK from PR #160's passing `AUTV Full Application Debug` workflow. This is an **update candidate**, not an approved replacement for the TV build.
2. On the Australian TV open Settings → Australian services → ABC iview. Select **Connect ABC iview**. Record whether ABC actually issues a four-character code and whether it is accepted at `https://www.abc.net.au/linktv`.
3. If approved, check the real linked-account state, discovered live channels and expiry/reconnection. Playback must be separately marked blocked if no approved HLS signer is available.
4. Open Network 10, select **Try real 10 account linking**. Attempt the code request; note whether the first-party configuration returns a real generate-code URL, a genuine code, or HTTP 404. Do not use a code from another TV app.
5. Optionally choose **Play 10 VIC native trial**, then verify actual picture/audio, discontinuities and embedded advertising; do not treat successful experimental video as broadcaster entitlement.
6. Return to 7plus, 9Now and SBS and verify existing native playback.

## Still required for production acceptance

- ABC: real device approval/token verification on the physical television and a **permitted** HLS authorisation/signing contract. The experimental link cannot supply one.
- 10: a functioning registered first-party config/device-code flow, approved media signing, live channel grants, and IMA/DAI playback acceptance. The public config returned HTTP 404 in Actions.
- Neither an open-source Kodi plug-in nor the official applications' credentials may be impersonated, imported or silently reused. Never strip ads or DRM.
- Passing synthetic tests, generating an activation code, and exposing a stream in a guide are not equivalent to playing live television with picture and sound.
