# Codex work package — ABC iview native AUTV connector

**Priority:** Next. **Repository:** safetyblade/autv. **Goal:** complete ABC iview live-channel playback inside AUTV on Australian Android TV, with full provider account/session rules where required.

## Review before code
- Existing decision: `docs/spikes/ten-abc-seven-native-decision.md` in PR #74, or review PR #74 if not merged. It documents a historical ABC add-on with stream HMAC/authorisation and no demonstrated current device activation protocol.
- Prior art: https://github.com/aussieaddons/plugin.video.abc_iview (especially `comm.py`, `live.py`, `play.py`, `config.py`; last documented repository head May 2023, so **historical only**).
- Survey current maintained Kodi/SlyGuy/other open source implementations, official ABC iview support and relevant Android TV sign-in/user authentication and stream interfaces. Establish current availability; do not assume 2023 URLs/keys still work.
- Existing AUTV adapters: `NineNowAdapter`, SBS adapter, provider account controller, shared QR/pending activation, encrypted session store, native Media3 and guide routing.

## Feasibility first
1. Determine whether ABC live streams need sign-in for the owner's intended Android TV, and whether any permitted channel listing/live playback works without sign-in. Do not mistake optional consumer account login for mandatory stream authorisation.
2. Where account linking applies, document the **current** provider-issued device code or official QR flow, activation endpoint, polling/expiry/refresh and whether third-party client integration is permitted. Consumer URLs `https://www.abc.net.au/linktv` and `https://www.abc.net.au/link` can be entry-point references; do not fabricate code/prefill or API.
3. Discover the official current Australian live-channel catalogue, channel identities, schedule and region configuration (Melbourne/VIC as appropriate), including ABC's main and companion feeds returned by the provider.
4. Trace an approved original playback request to HLS or DASH, media headers, potential licence/protection and original ad/session metadata. Do not port embedded signing secrets from historic Kodi packages or impersonate an official app.
5. If legally and technically feasible, build a small ABC-only connector using the existing provider abstraction, not a web proxy or custom playlist export. Keep broadcaster source identity stable and private.
6. Provide connected / not connected / expired / unavailable states and device QR where genuinely supported, maintain explicit sign-out, session expiry and restart continuity.
7. Prove one mainstream ABC live channel with actual video **and audio** on the owner's device before expanding to returned companions; ensure Favourites, guide/remote and FAST recover properly.

## Delivery
- Source-cited architecture decision, current API vs historic method, exact blockers and support/permission status.
- Focused ABC draft PR only when feasible, Android builds and repeatable tests, redacted diagnostics, explicit physical-device QA plan.
- No fabricated stream URLs, no token/cookie dumping, no DRM/region/ad bypass, no merged PR without owner approval.
- **Do not modify Seven playback or 10 integration in this work package.** Preserve 9Now/SBS and FAST.
- Distinguish code tests from owner-confirmed real broadcaster playback.
