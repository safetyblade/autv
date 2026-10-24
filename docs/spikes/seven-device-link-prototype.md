# 7plus device-link and catalogue prototype

24 October 2026 (Australia/Sydney). This implements account linking and channel
metadata, not native 7plus playback. It builds on the shared QR/SBS work in draft
PRs #72 and #73. No 10 or ABC connector is enabled.

## Evidence and narrowed architecture decision

The [previous decision](https://github.com/safetyblade/autv/pull/74) identified missing client,
location, licence and advertising contracts. Its blockers still apply to native
playback. This prototype narrows implementation to the independently inspectable
account/metadata protocol; it does not claim to satisfy the playback acceptance.
The prior decision is in draft PR #74 if that file is not yet on this branch.

Read SlyGuy 7plus 0.5.8 `api.py`, `plugin.py`, `constants.py` at mirror commit
[76d5a37e885f3943e0ec8eede05ee6ccefe8acef](https://github.com/matthuisman/slyguy.addons/blob/76d5a37e885f3943e0ec8eede05ee6ccefe8acef/slyguy.7plus/slyguy.7plus-0.5.8.zip).
The official consumer entry point is https://7plus.com.au/connect. The package's
public API configuration and protocol are evidence, not AUTV client approval.
No source implementation is copied; no application signing key, official-app
User-Agent, fixed postcode, Sydney fallback, advertising identity or licence
workaround is used. AUTV uses a truthful User-Agent, a random local device UUID
and the observed `androidtv` platform class. Client permission remains unresolved;
keep experimental and draft, not production distribution.

Decision: build a small separate adapter for activation, token refresh and guide
metadata. Reuse the shared account controller, encrypted persistence and QR UI.
Keep the current player unchanged. Until a provider-issued media/licence descriptor
and ad lifecycle are implemented and owner-tested, every 7plus tune explicitly
fails without making a playback/CDN/licence request. Do not fabricate a successful
player or use an external app launcher as the implementation.

## Account and network boundaries

`SevenPlusAdapter` uses form POSTs on `auth2.swm.digital`:

- `/account/device/authorize`: platform/regSource, local device UUID and
  `locationVerificationRequired=true` (never disable location checking).
- `/connect/token`: device code until approved, or refresh token when expired.

The server challenge supplies `user_code`, `device_code`, `expires_in` and
`interval`. Bounds constrain lifetime/interval. The returned verification address
must be HTTPS on `7plus.com.au/connect`, with no explicit port, credentials or
fragment. Complete URL query data is discarded. QR pixels are generated locally
and encode only the reviewed bare address; the code stays visible separately.
No manufactured prefill, access token, device code or private session enters QR.

Known OAuth pending, slowdown, expiry and denial are handled separately. Empty or
unknown error responses fail as unsupported instead of pretending approval is
pending. These shapes are fixture-tested, not live-confirmed. Complete tokens
plus bounded `expires_in` are required before Connected; missing refresh tokens
on initial approval fail. Refresh may retain the existing refresh token if the
server does not rotate it. Temporary network failures retain the session; terminal
refresh failure clears only Seven and requests reconnection.

The encrypted namespace is `autv.seven.session.v1` / `seven-session-v1`;
`seven-device-v1` stores only a random UUID in noBackupFilesDir. Pending challenge
records reuse the existing encrypted store and original deadline across adapter
recreation. Explicit cancellation/sign-out deletes Seven's credentials/challenge
locally; no server revocation is claimed. The account mutex/generation and tune
routing are separate from Nine and SBS. Successful connection survives discovery
failure. Sessions survive ordinary restart and correctly signed in-place updates,
not uninstall, clear-data or a device factory reset.

The HTTP transport permits only those exact auth paths, the fixed market endpoint
and one bounded catalogue query shape. No redirects, cookies, bearer headers,
raw response/error logs, CDN or licence calls are added. Form secrets are sent
only to the reviewed token endpoint. The public distributed-client API value
stays on the three reviewed API hosts. Network bodies have a 2 MiB cap and timeouts.

## Catalogue and explicit playback boundary

After account approval/refresh, the adapter obtains the market ID from
`https://market-cdn.swm.digital/v1/market/ip/?apikey=web`. Failure or missing market
stops discovery. It then requests `component-cdn.swm.digital/content/live-tv`
using that returned market and the inspected API/platform versions. No invented
region/postcode or location bypass is used. Deduplicated `ChannelItem.channelId`
values identify private `seven` rows; signed playback URLs are not cached.
Existing internal number/favourite allocation remains provider-scoped.

Settings says that the account is connected and channels are listed, while native
7plus playback is unavailable. Selecting a row produces a sanitized unsupported
message and keeps FAST recovery available. This is not an all-channels release.
The next work is an authorized original Brightcove descriptor and platform
Widevine licence exchange, source-specific DASH/HLS handling and ad obligations,
then real video/audio proof on one returned channel. Android's DRM capability
alone does not establish that contract.

## Validation and owner retest

JVM tests cover encrypted-store-independent adapter logic: same pending challenge,
official QR destination, token completeness/refresh, terminal/unknown errors,
detected-market discovery/deduplication, no market fallback and explicit unsupported
tune routing. App tests decode the Seven QR to the bare official URL and reject
unreviewed transport destinations. Android fixtures exercise actual Settings →
Seven → QR → simulated token approval → catalogue, unsupported tune, and local
sign-out without clearing Nine. They do not call any broadcaster or play Seven.
Existing Nine/SBS/store/update regression checks remain in the workflow.

No real 7plus account, challenge, catalogue, manifest, licence, ad or video/audio
has been verified by the agent; broadcaster API hosts are blocked by this
execution environment. Fixtures are not a physical-phone scan or Kogan TV proof.
Owner retest: use Settings → Australian services → 7plus → Connect; scan the QR
or type the official address and enter the code. Report only the displayed state
and sanitized error; do not paste codes, tokens, signed URLs or raw API bodies.
Check connection separately from channel discovery, cancellation/expiry, reopening
and sign-out isolation. Expect playback to remain unavailable in this build.

No public FAST playlist, guide publishing, channel numbering, six-hour refresh,
Nine/SBS resolver, permanent signing workflow or existing player is changed.
The prior permanent-key setup is still required for production in-place updates;
disposable debug artifacts are not permanent update distribution. No auto merge.
