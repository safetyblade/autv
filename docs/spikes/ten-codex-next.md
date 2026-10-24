# Codex work package — Network 10 native AUTV connector

**Priority:** Immediately after ABC, or independent isolated work if safe. **Repository:** safetyblade/autv. **Goal:** native Australian live channels for 10 with legitimate sign-in and normal broadcaster advertising, inside AUTV.

## Required comparison / evidence
- Review `docs/spikes/ten-abc-seven-native-decision.md` in PR #74 (or current main if merged).
- Inspect newest available packaged `slyguy.10play` and shared SlyGuy sources at https://github.com/matthuisman/slyguy.addons ; previous 0.4.5 package evidence points to `api.py:generate_x_n10_sig`, `_request`, `_config`, `device_code`, `device_login`, `play`. Compare with other maintained integrations and source dates; do not presume they are current.
- Existing AUTV working 9Now/SBS connector, shared QR, session manager, guide, Media3; only reuse provider-neutral code.

## Feasibility and hard blockers
1. Verify today's official Network 10 branding/service URLs and consumer Android TV activation. Historic `https://10.com.au/activate` is a lead, not evidence of a still-active protocol.
2. Determine whether a **legitimate approved client/request-signing mechanism** exists for AUTV. Historical SlyGuy requests use `X-N10-SIG` derived from embedded application signing material; **do not copy/reconstruct private signing secrets**, impersonate official clients, or treat obtaining a signature as provider permission.
3. Investigate official live-channel catalogue, regional channel selection for Victoria, current device activation/poll/refresh and required entitlement.
4. Trace an authorised live playback session, Google DAI or other ad service if used, original stream/manifest (HLS or DASH), Widevine if required, ad beacon/consent/event lifecycle and session expiry. Owner explicitly wants normal advertising left intact.
5. Media3 may need appropriate DAI/IMA integration, but do not introduce an SDK without verifying a permitted provider integration contract and source descriptor.
6. Only if supported access exists, build minimal isolated Network 10 adapter and QR-first device linking with provider-issued challenge, secure private storage, stable provider channel IDs and a first-channel native video/audio proof.
7. Test through a complete real commercial break and continuing live session; validate normal Guide/Back/CH+/CH−, session expiry, provider-scoped signout, Cast limitations, FAST fallback.

## Delivery
- Evidence-backed GO/NO-GO decision, citations to exact current source code and provider docs, any registration/permissions needed, precise blocker rather than speculative workaround.
- Minimal single-provider draft PR, tests and Android build only if feasible; real device playback evidence separate.
- Do not publish provider credentials, signed URLs or client secret values; no geobypass, ad stripping, DRM circumvention, copied embedded signing material or external-app fallback presented as native success.
- **Do not touch Seven or ABC code in this PR**, and protect existing 9Now/SBS/FAST.
