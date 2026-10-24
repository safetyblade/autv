# AUTV — Implementation-first Australian television completion

**Owner decision:** Stop recurring feasibility-only writeups. Implement and test reusable native playback capabilities now. Then connect them to permitted broadcaster sessions. No unapproved credential/signature copying, DRM circumvention, ad removal or geo workaround.

**Research precedents**
- UK multi-broadcaster aggregator EasyPlayTV: https://scottrix.github.io/EasyPlayTV-docs/index.html — understand multi-provider routing, distinguish library-dependent behaviour from native Media3.
- SlyGuy actual packages: https://github.com/matthuisman/slyguy.addons — 7plus, 10play, au.freeview and shared playback helpers; inspect package zip/function/version, not descriptions.
- Recent ABC iView+: https://github.com/JCalvi/plugin.video.abc_iviewjc — distinguish TV login from live-media authorization; original feed metadata.
- Android TV architecture: https://github.com/opentvproject/opentv — user experience/player structure, not a source of Australian accounts.
- Official Android player capabilities: https://developer.android.com/media/media3/exoplayer/ad-insertion and https://developers.google.com/ad-manager/dynamic-ad-insertion/sdk/android/get-started/exoplayer-extension

## Immediate deliverable A — IMPLEMENT the media capability

On a new isolated feature branch, extend AUTV's provider-specific playback contract and Media3 source factory to model, without exposing secrets:
- Original HLS (currently implemented); original DASH (new), stream MIME, per-origin headers, transient expiry.
- Optional **provider-issued** Media3 DRM configuration (Widevine only when supported, never infer a licence URL).
- Server-stitched HLS with original ads and playlist reload/discontinuities; HLS interstitials where present and supported.
- Google IMA live DAI source with **valid provided asset key/config** and correct ad loader/session lifecycle. Use the official SDK, not an improvised master URL or player-side ad suppression.
- Failure-safe return to previous FAST source, no account/session leaks to Cast or public M3U.
- Preserve existing Nine, SBS, Pluto, FAST and current settings. Feature gate new playback routes; no broadcaster adapter enablement required to test.
- Unit tests AND instrumentation tests with official public sample HLS, DASH, IMA DAI live and DRM sample streams as applicable. Never claim a protected sample proves a broadcaster entitlement. Build/test APK.

**Do not stop after writing an ADR**. Make code changes and supply build/test evidence. If part of an SDK is not supported, implement the other proven branches and name the exact blocker.

## Immediate deliverable B — ABC independent adapter

Read current investigation PR #81 and the ABC iView+ source. Implement provider-neutral and ABC-specific parts that are supported by current evidence, keeping no-login website live feeds and account-linking separate. Determine current authorized channel metadata and original stream requirements. Where a supported native playback flow is available, wire one VIC ABC TV feed into new player. Do not copy packaged HMAC keys or silently bypass provider authorisation. If an ABC-approved client registration is essential, give a concrete request/field list and retain tested non-secret adapter code. **Do not create another research-only PR unless there's literally nothing testable to implement.**

## Immediate deliverable C — Network 10 independent adapter

Read current investigation PR #82 and actual current SlyGuy 10play package, plus current yt-dlp only for comparisons (VOD != live; no ad-filter code). Implement DAI player plumbing in A and create 10 adapter interfaces for device linking, catalogue, authorised playback descriptor and original ad session. Require permitted client details; do not copy embedded X-N10-SIG signing keys or impersonate LG/official Android TV profiles. Validate with official Google live DAI test stream first and real 10 session later. Return exact registration dependency if unsupported.

## Deferred D — Seven

Hold PR #75/#79 and existing descriptor error `7-DESCRIPTOR HTTP 400 non-JSON` until A-C. Revisit SlyGuy Seven and IPTV AU routes and the original authorised provider descriptor after proven player architecture exists. The owner accepts normal ads and requires them preserved. See `docs/spikes/seven-hold-2026-10-24.md`.

## Implementation acceptance and output contract

Deliver three non-overlapping draft implementation PRs if feasible, beginning with A. Per PR report: files changed, SDK versions, successful build/test logs, what actual audio/video played, which real provider request remains blocked, and steps for owner device QA. Every PR must have executable code and tests, not just documentation. **Do not merge automatically.** Do not invent live playback success. Seek supported broadcaster partnership/interface where access itself is restricted. Keep no user credentials in GitHub or logs.

The goal is a real TV application: authentic local programmes, normal ads, complete live channel streams, remote navigation, stable sessions, unified Favourites and guide.
