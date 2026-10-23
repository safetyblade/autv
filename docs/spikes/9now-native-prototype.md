# 9Now native provider prototype

Status: experimental implementation. The owner reports native Seinfeld playback
succeeded on their device. Other live channels are not yet device-verified.
Scope: Settings → Australian services → user-initiated Nine device activation →
discover 9Now live channels/events → existing AUTV guide → retained native Media3 player.

The user explicitly requested implementation of this structure after the initial
[feasibility decision](https://github.com/safetyblade/autv/blob/docs/9now-native-feasibility-decision/docs/spikes/9now-seinfeld-native-decision.md).
This prototype does not establish provider approval or satisfy the brief's live
playback acceptance gate. Keep the PR draft until those prerequisites are verified.

## Evidence and architecture

The complete PR #66 brief was read first. Upstream evidence is 9Now mirror
[0.4.6, 12 October 2026](https://github.com/matthuisman/slyguy.addons/commit/9e1bcb6ae6dc4207cac32e9229178eb281c749ee),
specifically packaged resources/lib/api.py, plugin.py, constants.py and addon.xml;
Common [0.87.2](https://github.com/matthuisman/slyguy.addons/commit/76d5a37e885f3943e0ec8eede05ee6ccefe8acef)
session.py, userdata.py, inputstream.py and util.process_brightcove were inspected.
The current index lists 9Now, but parity of its live packaged version with the
mirror remains unverified. The historic predecessor's comm.py/play.py and GPLv3
licence were read for history, not as current authentication evidence.

The present SlyGuy package requires device sign-in, unlike its 2021 article.
It obtains device codes from Nine, polls for approval, refreshes a token, reads
the CTV livestream catalogue and resolves a live channel's stream.url.
This is a plausible HLS integration surface; it is not a documented public SDK.
Nine's official [activation help](https://help.9now.com.au/hc/en-au/articles/35830491543833-How-do-I-log-in-to-9Now-from-my-Smart-TV)
confirms the owner completes sign-in with Nine. Consumer client registration and
permission for AUTV remain an external release gate. No upstream code was copied:
the packages have no affirmative reuse licence, and their code is not treated as
a translation template.

The prototype makes the current consumer endpoint protocol explicit and fakeable
through NineNowTransport. It uses the observed device client identifier; it must
be replaced with a provider-approved client arrangement if Nine requires one.
No claim is made that an accessible consumer endpoint grants integration rights.
No provider permission request or message has been sent.

## Implementation boundaries

- ProviderChannelId, ProviderChannel, ProviderSession, DeviceActivation,
  ProviderPlaybackRequest and ProviderAdapter separate identity, metadata,
  account state and ephemeral playback.
- Settings presents Connected / Not connected / Waiting / Expired / Unsupported,
  Connect, channel refresh and sign-out/cancel. Password entry stays exclusively
  on Nine's activation site. Closing Settings does not cancel owner approval;
  the explicit Cancel button does.
- A successful device session triggers metadata discovery. The returned
  live catalogue is exposed, with actual referenceId values. Other services remain
  visibly unavailable until their own connector is implemented.
- Access/refresh tokens are encrypted with an Android Keystore AES-GCM key.
  Ciphertext is in noBackupFilesDir. Tokens and activation records have redacted
  string representations; pending activation records are encrypted, device-local and expire with the provider deadline. Sign-out
  serializes against pending IO before deleting credentials and the key.
- Catalogue records contain no playback URLs. Neither session nor private
  channels are written to guide.json, playlist reconciliation or the public
  guide cache. TV-home publishing uses only the original FAST catalogue.
- Native-only negative guide numbers are internal routing keys, never published or
  displayed as numbers. Seinfeld retains -9001; other references get unique,
  persisted numbers that survive refreshes and catalogue reorderings. Existing FAST channel numbers and
  favourite keys are unchanged. Provider favourites use namespaced official IDs;
  Seinfeld retains provider:9now:seinfeld for compatibility. No token or stream URL
  participates in identity.
- The same guide, remote OK/long OK handler, retained model and player are used.
  A provider row is tunable without a fake stream URL. Australian TV appears
  only when discovery supplies live channels. Until then Settings remains available.
- Each tune re-fetches the provider descriptor. The returned HTTPS stream URL is
  used unchanged: no SSAI unwrapping, alternate raw URL, stripping query values,
  cookies harvested from a browser, proxy, geo workaround or ad-blocking logic.
  Account bearer headers are sent only to the provider catalogue, never a CDN.
- This proof accepts HLS only. Declared DRM is rejected, and Media3 errors restore
  the last working FAST channel. There is no DRM licence exchange or content-key
  extraction. A protected variant needs a separately authorized platform-CDM
  implementation; the presence of Widevine on a device is not authorization.
- A fresh HLS MediaSource scopes provider HTTP headers to that request. The old
  FAST play(url, number) path keeps its original media factory. Late resolution
  is cancelled/ignored when tuning FAST or signing out.
- Cast is unavailable for account-bound provider rows and cannot receive the
  account session. Connecting Cast while a provider plays restores FAST.
- Current programme metadata is optional in the contract; this first prototype
  does not import provider EPG and uses the existing neutral schedule fallback.

## Validation

Focused JVM tests cover user-initiated activation, polling interval/expiry,
pending approval, refresh, refresh-denial versus offline handling, logout,
Seinfeld-only discovery, URL-free catalogue records, unchanged ad URL resolution,
declared DRM rejection, namespaced favourites, native tuning without a URL,
CH navigation and exclusion from TV-home publishing.

ProviderPlaybackTest uses the real retained player and local debug HLS assets.
It verifies provider tuning, pause/resume, sign-out/FAST recovery and resolve
denial retaining FAST. Fixture video is not evidence of live 9Now playback.
The separate Native provider prototype workflow compiles instrumentation tests,
runs core-data/core-guide JVM suites and builds a debug APK. It does not change
the existing manual Native app workflow or any FAST refresh workflow.

Live activation, an authenticated Australian catalogue, actual live manifest
format/codecs/protection, ad delivery/callback obligations, sustained Seinfeld
video/audio, physical Kogan remote, phone/tablet rotation/lifecycle, and physical
Cast regression remain unverified. The attached cloud environment was offline
while changes were prepared; direct official page reads returned 403, which
does not establish an Australian entitlement or DRM failure.

Before approval, run on an Australian supported device with the owner completing
official activation privately. Inspect only redacted status/format diagnostics.
Verify pause, Back/re-entry, code expiry/cancel, session expiry, denied region,
unavailable/withdrawn channel, sign-out, favourites, Quick Guide, Full EPG and
CH keys; verify FAST remains usable on every failure. No live credentials or
signed URLs belong in GitHub/CI/chat. Do not merge automatically.

## Preservation and migration

No public playlist, generated guide/EPG, channel number, source script, Pluto
workflow or six-hour refresh change. No FAST cache or favourite migration.
The optional service stays disconnected until the user initiates activation.
A fallback external-app launcher has not been implemented.

Nine's terms restrict use to personal streaming in Australia and prohibit
circumventing DRM and ad/technical controls. Treat provider integration permission,
consumer API stability, ad obligations and valid licence acquisition as release
gates. If they cannot be met, recommend the official 9Now app as a fallback,
without calling external playback native AUTV success.


## Follow-up: connected account with no discovered channel

A user device screenshot shows Connected followed by an empty Seinfeld discovery
result. This is user-reported activation evidence, not agent-verified live
playback. The original parser searched only channels and required slug=seinfeld;
it omitted the live-event catalogue path observed in SlyGuy and rejected known
Seinfeld 24/7 title/slug variants.

Discovery and tune now share one exact normalized title/slug matcher over channels
and live-event entries. Entries sharing the same official referenceId are merged,
preferring a returned stream descriptor over a metadata-only duplicate; ambiguous
different identities are rejected. No stream URL is fabricated or unwrapped.
A listed channel without a native stream has its own unsupported-playback result.

Empty discovery now reports only channel/event/match counts, not a false claim
that the provider channel is unavailable. These counts contain no account data,
channel list, credentials, activation codes or playback URLs. Regression fixtures
cover title variants, events, duplicate rows, ambiguity and missing descriptors.
The actual device catalogue is still unobserved; a successful live tune remains
to be verified by the account owner.


## Follow-up: browser activation resets to Not connected

The owner reports that Nine's linking page spins, then fails, and AUTV returns to
Not connected with a new-code button. This does not establish the server's exact
rejection cause. Two app-side failure modes are addressed: losing an in-memory
challenge when the app/ViewModel is destroyed during browser handoff, and ending
authorization on the first transient polling failure.

A pending device challenge is now encrypted in noBackupFilesDir using the same
Keystore protection as the session. App restart resumes the original unexpired
challenge without a new code request. Explicit cancellation, expiry, rejection
and successful token exchange delete it. An absent session file must not erase a
pending challenge. Destruction of the UI no longer signs out an incomplete flow.
No password, browser cookie import or background token-harvesting service is added.

Timeouts, provider rate limiting and temporary outages retry within the original
expiry, with backoff. Known pending, slowdown, denial and expiry responses have
separate handling, including nested pending codes. The UI shows the stage and
only allowlisted HTTP status numbers on failure, never raw response bodies.
Settings survives Activity restoration, and PiP is disabled during device linking.
When using the same phone, return to AUTV after submitting the activation code;
another phone/computer can also activate it while AUTV stays open.

Fixtures cover a transient poll failure, backgrounding, old ViewModel destruction,
new model restoring the same challenge, successful approval and explicit cancel.
A Keystore test checks encrypted challenge restoration while no session exists.
The provider's real activation rejection/acceptance remains an owner-device retest;
these fixtures are not live 9Now evidence.


## Follow-up: activated and discovered, but no stream.url

The owner's latest screenshots show Connected, Device activation complete and a
Seinfeld guide row, followed by unsupported native playback. These are owner-device
evidence for activation and discovery, not successful playback or an inspected
catalogue response.

Reinspection of the pinned 9Now 0.4.6 package established that live_tv routes
live-event rows through Brightcove using brightcoveId (or referenceId), whereas
ordinary channels use stream.url. The prototype previously omitted that branch.
The isolated resolver now requests the playback descriptor for Nine's returned ID,
using the public distributed-client playback policy on the exact Brightcove account
endpoint. No account bearer is forwarded to Brightcove or media hosts. A duplicate
metadata row cannot mask the corresponding live-event resolution route.

The resolver accepts only provider-returned HTTPS HLS sources without declared DRM,
and preserves the original source URL and query. DASH-only, protected or missing
sources remain explicit failures. No alternate raw URL, ad URL unwrapping, licence
exchange or password collection is added. Public client configuration is not proof
of provider integration permission; the existing release gates still apply.

Fixtures verify numeric and reference IDs, token isolation, metadata-only discovery,
original ad/session query preservation and rejection of protected, DASH and HTTP
sources. The exact owner-device descriptor remains unknown and requires a retest.


## Full live catalogue and provider settings

After the owner confirmed native Seinfeld playback, discovery was expanded to all
returned channels and live-event entries. Duplicate official references collapse;
non-live event types are excluded. Resolution re-fetches the catalogue and chooses
the requested reference, rather than routing all rows to Seinfeld. Every native
channel gets a unique persisted guide number; the original Seinfeld favourite and
number are retained. Live events can be upcoming or unavailable, and protected or
unsupported streams still fail explicitly. This is the live catalogue for the
existing NSW region request, not a VOD/episode browser or all Australian regions.

Settings now starts with branded Australian service cards, then opens the chosen
provider's detail page. 9Now shows saved connection status, channel count, refresh,
official device activation and sign-out. ABC iview, SBS On Demand, 7plus and 10 are
labelled unavailable and collect no credentials. Icons are bundled locally, with
sources in docs/australian-provider-assets.md. Touch and TV focus use the same
controls; the connected player remains retained while Settings changes pages.

Distribution signing/versioning and the one-time secret setup are documented in
docs/android-updates.md. The Native app workflow now builds signed release updates;
PR workflow APKs remain development fixtures. A dedicated upgrade fixture checks
an encrypted session and preferences across adb install -r of a newer version.
