# AUTV — 9Now Native Connector / Seinfeld 24/7 — Codex Workspace Brief

**Status:** Commissioned technical spike. **Target:** native provider connector (Option B), not a launcher as the primary solution.
**Repository:** https://github.com/safetyblade/autv
**Baseline:** start from current main; branch from latest main and avoid concurrent guide/Pluto changes.
**Proof channel:** https://www.9now.com.au/live/seinfeld (also https://www.9now.com.au/seinfeld-247).
**Acceptance:** user can authenticate where required and watch Seinfeld 24/7 *inside* AUTV on a supported Australian Android TV, without harming FAST playback. Do not claim success from an HTML page or manifest alone.

## Mandatory external prior art — read code, not just blog summaries

1. SlyGuy add-on mirror: https://github.com/matthuisman/slyguy.addons — inspect `slyguy.9now/`, the current Python add-on files and its shared dependencies. The mirror can differ from current packaged release; inspect update dates and identify current release source.
2. Author's explanation: https://www.matthuisman.nz/2021/02/9now-kodi-add-on.html — historically supports 9Now live TV, live events, programme guide and IPTV Merge. The **2021 article says no login required**, which may be **outdated** versus the current website's login requirement. Verify live behaviour rather than relying on that old statement.
3. Current SlyGuy package index: https://slyguy.uk/.repo/addons.html — check current version and 9Now add-on availability.
4. Historic predecessor: https://github.com/glennguy/plugin.video.9now — read for design history only, not a current authenticated implementation.
5. Official provider: https://www.9now.com.au/live/seinfeld and https://help.9now.com.au/ . 9Now currently requires account sign-in for live web viewing.

Inspect and document exactly how the current SlyGuy 9Now code: discovers channel identities, retrieves programme data, establishes any account/session state, asks for playback descriptors, handles manifest formats, cookies/headers, DRM declarations and Kodi InputStream Adaptive/Widevine. Clearly label which findings are directly evidenced in current code versus assumptions or historical behaviour. Check licensing before reuse; do not mechanically translate or copy code unless licence permits.

## Local code map and preservation requirements

Inspect `native/README.md`, `android/app/src/main/java/dev/prestwich/autv/MainActivity.kt`, `android/app/src/main/java/dev/prestwich/autv/PlaybackModel.kt`, guide/player models, local Favourites implementation, Media3 setup, Cast and remote-key handling. The native application already uses a retained Media3 player with JSON guide and device-local favourites. Determine real source boundaries, do not assume undocumented features are absent.

DO NOT touch `playlist.m3u`, `custom.m3u`, `channel_guide.csv`, `guide.json` generation, `epg.xml.gz`, Pluto refresh workflow, existing channel numbers, or the six-hour FAST publishing job. Australian services must live in a separate optional native-only provider catalogue, not the public generic M3U.

## Stage 1 — evidence-backed feasibility

- Confirm the official 9Now Seinfeld channel exists and the authenticated user can play it from an Australian network.
- Determine whether the SlyGuy model remains current; record data flow and endpoint characteristics **without writing credentials, bearer tokens, signed URLs or cookies into logs, commits, CI artifacts or chat**.
- Document whether provider interfaces are supported/public or unofficial/fragile; evaluate terms and service permissions. Do not bypass DRM, authentication, geo-restrictions or ad systems.
- Explore allowed sign-in: prefer official browser/device activation or a permitted session handoff. Never build a credentials-harvesting form or request account passwords in source, issues or CLI flags.
- Inspect the actual playback format: HLS or DASH, codecs, Widevine protection, required licence exchange. Media3's Widevine capability is not licence authorization. Stop native playback attempts if legitimate licence acquisition is not available.
- Determine if permitted provider interfaces allow stable channel metadata, guide listings and session-scoped playback references; no long-lived M3U URL assumption.
- Evaluate Android TV Kogan remote support; mobile/tablet compatibility; Cast and login expiry.

## Stage 2 — design the smallest provider adapter

Propose a typed `ProviderChannel` and `ProviderPlaybackRequest` abstraction separating channel catalogue, account/auth state, play request, DRM/official-player capabilities, unavailable/error states, and EPG (only when available). Keep provider identity stable across reauth, app restart and channel refresh.

Suggested separation: `FastHlsChannel` keeps existing Media3 route unchanged; `NineNowChannel` uses its own provider adapter and supported Media3 DRM or official supported playback when permitted. Do not introduce a universal proxy, background token harvester, hosted scraper or server. Avoid duplicating guide/player screens.

User-facing optional `Australian TV` service section with connection states (Not connected / Connected / Session expired / Unsupported). Never publish the user's service channel list or credentials into GitHub. Long-press OK favourite must keep working; existing CH+/CH-, short OK, Back, Quick Guide, Full EPG, offline handling and Cast must not regress. If Cast cannot use the protected provider stream, mark unavailable rather than pretending it works. Account sign-out must clear provider session and cached sensitive records.

## Stage 3 — one channel proof

**Only Seinfeld 24/7.** Prove:
1. User-initiated, safe 9Now authentication or documented no-login access where current service allows it.
2. Resolve Seinfeld's official live channel identifier and metadata from current 9Now state.
3. Begin actual video/audio playback inside AUTV using permitted platform APIs and a valid licence where required.
4. Handle auth expiry, denied region, unavailable stream, DRM unsupported, pause/back and re-entry gracefully.
5. Return to last working FAST channel without poisoning player state.
6. Test TV remote operation and Android phone/tablet; report explicitly if physical-device verification is not possible.

If live native playback is blocked by unsupported authentication, DRM licensing, provider terms or device-specific requirements, stop and document the blocker. Official-app deep-link fallback can be shown as a fallback **only**, not presented as success for Option B.

## Deliverables and decision gates

First deliver an architecture decision record: exact upstream references/files, flow sequence, integration surface, terms/licence risks, secrets model, and GO/NO-GO recommendation. Then implement a minimal **draft PR** *only if* there is a legitimate practical in-app playback route. Include focused unit/integration tests, Android build, playback evidence (with redacted diagnostics), migration and regression assessment. Do not merge automatically.

Final report explicitly distinguishes: (a) code studied, (b) API or stream discovery, (c) manifest success, (d) video/audio played, (e) physical Kogan TV and mobile tests. No speculative 'working' claim.

## Security notes

The account password was previously pasted in chat. Treat it as compromised, do not use it, and never propagate it to Codex or any repository file. User should reset it privately and complete any login themselves through an official flow. Secrets and session artefacts must remain encrypted/device-local where appropriate, never public repo, logs or CI.
