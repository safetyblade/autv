# Seven / 7plus — preserved hold point (24 October 2026)

**Decision:** Park 7plus playback investigation until ABC iview and 10 connector work is completed. Do not discard the 7plus account/catalogue prototype or close its branches solely to simplify work. Avoid 7plus playback edits in ABC/10 PRs.

## Owner-device facts

- Account successfully linked; device activation complete.
- Authenticated catalogue previously showed 53 live channels; later screenshot shows **52**. The exact missing entry is unknown; do not infer a removal without comparing returned reference IDs.
- Guide contains Seven, 7mate, 7two, 7Bravo and other Seven/themed entries; discovery is **not** evidence of playable video.
- First failure: `7-IDENTITY` — provider playback identity not resolved.
- Later builds reached `7-DESCRIPTOR (HTTP 400)`: provider playback descriptor request rejected, before manifest/player/ads.
- Most recent image error: **`7-DESCRIPTOR (HTTP 400) Descriptor rejection: non-JSON response. No allowlisted field hints.`** The HTTP response body was not exposed, nor should it be copied from private sessions.
- The 7plus settings screen may misleadingly say "7plus is unavailable"; account is still Connected, so failure messaging should distinguish playback from account/catalogue status.
- Owner provided diagnostic themed-channel URL `https://7plus.com.au/live-tv?channel-id=Mythbusters`; this offers a valid `channel-id` *website slug*, not a proven media/Brightcove identity or stream.
- Owner wants ordinary AU channels **including normal broadcaster commercials**; avoid ad stripping, bypass, geographic workarounds or external player handoff as final solution.
- Earlier supplied UUID `01a166f5-2aca-7276-91c9-a174b18520aa` is **not established** as a Seven content identifier. Do not send it as a playback ID without independent provider evidence.

## Code/research handoff

- Existing experimental account/catalogue PR #75: https://github.com/safetyblade/autv/pull/75
- Stacked native playback/diagnostics PR #79: https://github.com/safetyblade/autv/pull/79
- Prior report: `docs/spikes/seven-playback-recovery.md` on Seven playback branch, and `docs/spikes/seven-device-link-prototype.md`.
- Research prior art: SlyGuy `slyguy.7plus` 0.5.8; `plugin.video.au.freeview` (IPTV AU) 0.20.5; shared module 0.87.3, pinned by earlier Codex research to https://github.com/matthuisman/slyguy.addons/tree/485b3f4b8aae9080138bca5d4cf87bbf877c34d2 .
- IPTV AU uses regional third-party master metadata routing and does not use the standalone Seven account/playback resolver; success reports are historical, not a verified legal/current resolution.
- Upstream reports: https://github.com/matthuisman/slyguy.addons/issues/1092 and https://github.com/matthuisman/slyguy.addons/issues/1193 ; SSAI ad transitions may be fragile, but no native manifest playback has yet occurred in this owner test.
- Present narrow experimental code accepts certain provider-returned original HLS and rejects other ad/DRM configurations. Future implementation must investigate properly authorised original descriptor, Widevine/DASH if needed, and actual ad handling, not delete ads.

## When to resume

1. Rebase/consolidate Seven stacked work against current main **after** ABC and 10 tasks; preserve account connections and Nine/SBS.
2. Investigate exactly why the official videoservice descriptor request yields HTTP 400: request shape, reference/account IDs, method, legitimate client/platform parameters, location/market/advertising/session context, provider authorization.
3. Compare a main Seven row and the MythBusters themed row through provider-returned catalogue → content link → player metadata → authorized descriptor; do not assume they share playback requirements.
4. Keep sanitized stage/status only; do not log or commit tokens, signed URLs, challenge bodies or raw private error pages.
5. Only after descriptor success test manifest, audio/video, and continuous playback through a complete commercial break on an Australian TV.
6. Preserve normal advertising, official region controls, supported DRM/licence flows, FAST fallback, and existing provider secrets.
7. Do not claim working Seven on the strength of fixture tests or HTTP 200 alone.

**Status at handoff: account discovery working, native 7plus playback not yet working.**
