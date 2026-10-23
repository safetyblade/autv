# Australian services: activation and native connector feasibility

Investigated 23 October 2026. This distinguishes official consumer help, inspected
third-party protocol evidence, AUTV fixture tests, and owner-device results.
A broadcaster's QR screen is not permission for AUTV to use its device API.
No public third-party activation SDK or AUTV client approval was established for
any of these providers. No provider permission request was sent.

## Activation matrix

| Provider | Official HTTPS entry point | Device API evidence and permitted method | Prefill support | Polling / expiry | Connection confirmation | AUTV decision / restrictions |
| --- | --- | --- | --- | --- | --- | --- |
| 9Now | https://www.9now.com.au/activate | Existing AUTV device challenge/token/refresh flow, independently inspected against SlyGuy 9Now 0.4.6. Official help supports owner-entered TV codes. Consumer endpoint/client access is experimental; provider-approved client arrangement remains a release gate. | No documented code-prefill contract verified. AUTV encodes only the bare official URL. | Returned `interval` and `expires_in`; AUTV bounds them, persists the pending challenge encrypted, retries transient outages with backoff within the original deadline, and distinguishes pending/denied/expired responses. | Successful token exchange and encrypted session write. Channel discovery runs separately; an empty/failed catalogue does not undo successful account linking. | Implement QR over the existing flow only. Owner previously confirmed Seinfeld playback; this does not verify other live channels or the new QR handoff. |
| ABC iview | https://www.abc.net.au/linktv for Android TV; https://www.abc.net.au/link for newer specified TV families | Official help documents device linking. The inspected Aussie Add-ons source is a historic playback client without a current account-device challenge/polling flow. Current AUTV client registration and permitted activation API remain unknown. | Not established. Do not invent query parameters or interchange the device-family linking paths. | Official help states 20-minute codes; Android TV uses the four-character flow, newer specified TVs use eight letters. API polling interval and terminal response contract are unknown. | Official website/TV linked confirmation; AUTV would require its own legitimate token/session confirmation rather than inferring success from a browser page. | Research current approved device API first. Do not port historic HMAC/client impersonation or treat a public live feed as proof that account linking is available. |
| SBS On Demand | https://sbs.com.au/connect | SlyGuy 0.0.14 requests `auth.sbs.com.au/device/link`, polls `/device/poll`, refreshes `/refresh`. Official help supports phone QR sign-in and manual TV code entry. API access/registered device identity require provider confirmation. | API evidence includes a provider-issued `qrCodeURI` and separate `verifyURI`. Its precise prefill/deep-link contract has not been live-verified; validate any returned URL before future use, never synthesize parameters. | Challenge contains `interval`, `expiresIn`, `deviceCode`, `userCode`. Polling returns `accessToken` after approval, with optional refresh token. Exact denial/error response shapes need fixtures from a legitimate client contract. | Valid token response, encrypted storage, then independent guide discovery. Official help describes automatic TV sign-in after website confirmation. | Best next HLS/device-link investigation candidate. No SBS connector enabled by this PR. Never implement the upstream alternative email/password form. |
| 7plus | https://7plus.com.au/connect | SlyGuy 0.5.8 uses `auth2.swm.digital/account/device/authorize` and `/connect/token`, including device and location-verification options. Official help supports TV codes/QR. AUTV authorization/client registration remains unestablished. | Upstream consumes provider-issued `verification_uri_complete`, which contains the returned user code. This is evidence for a provider-issued complete URL, not permission to manufacture a `?code=` URL. Official domain/path and allowed fields need review before enabling it. | `interval`, `expires_in`, `device_code`, `user_code`; poll for `access_token` and refresh token. Location checks must remain intact. | Valid token/session storage, followed by separate catalogue discovery. | Device activation is plausible. Inspected playback explicitly negotiates Widevine/CENC/CMAF and uses Brightcove media descriptors; existing AUTV HLS-only/protection-rejecting proof is insufficient for protected variants. Needs an authorized platform-CDM/licence path and ad obligations reviewed. |
| 10 | https://10.com.au/activate | SlyGuy 0.4.5 loads platform config with generate/validate-code and refresh endpoints. Consumer requests use app-specific request signing. Official help documents five-character TV codes/QR; no AUTV integration permission established. | Inspected implementation uses the fixed activation URL; no supported code-prefill parameter verified. | Code generation returns an absolute expiry; inspected client polls at five seconds until expiry with a small deadline margin. Validate response contains JWT session fields after approval. | Validate-code returns session access/refresh data, then independent live catalogue/playback resolution. | Research approved request-signing/client credentials and Google DAI contract before implementing. Do not transplant embedded signing material, fake official device identities, manufacture manifest variants or copy the optional quality rewrite. |

## Primary official sources

- [9Now TV sign-in](https://help.9now.com.au/hc/en-au/articles/34314882301081-How-do-I-log-in-to-9Now-from-my-Smart-TV).
- [ABC account/TV linking, including separate Android TV and newer-TV flows](https://help.abc.net.au/hc/en-us/articles/8551749872655-How-do-I-link-my-TV-to-my-ABC-Account).
- [SBS QR account linking](https://help.sbs.com.au/hc/en-au/articles/13054751739279-How-do-I-connect-my-smart-TV-to-SBS-On-Demand-with-a-QR-Code)
  and [official connect address](https://help.sbs.com.au/hc/en-au/articles/16015847063567-How-do-I-sign-in-to-SBS-On-Demand-on-my-Apple-TV).
- [7plus connection troubleshooting](https://support.7plus.com.au/hc/en-au/articles/30542992397581-Connect-Your-TV-Issues).
- [10 activation](https://helpdesk.tenplay.com.au/support/solutions/articles/16000103948-activate-10-on-your-smart-tv).

These pages document consumer flows, not third-party developer API grants. None of
the unspecified prefill, polling-error or client-registration details are inferred
from a logo, official QR screenshot or a successful login to an unrelated device.

## Implementation evidence and playback implications

Pinned SlyGuy mirror [76d5a37e885f3943e0ec8eede05ee6ccefe8acef](https://github.com/matthuisman/slyguy.addons/tree/76d5a37e885f3943e0ec8eede05ee6ccefe8acef):

- [7plus 0.5.8](https://github.com/matthuisman/slyguy.addons/blob/76d5a37e885f3943e0ec8eede05ee6ccefe8acef/slyguy.7plus/slyguy.7plus-0.5.8.zip): resources/lib/api.py (`device_code`, `device_login`, `_refresh_token`, `play`), plugin.py (`_device_code`, `_get_live_channels`, `play_channel`), constants.py, addon.xml.
- [SBS 0.0.14](https://github.com/matthuisman/slyguy.addons/blob/76d5a37e885f3943e0ec8eede05ee6ccefe8acef/slyguy.sbs.ondemand/slyguy.sbs.ondemand-0.0.14.zip): resources/lib/api.py (device auth, live_channels, play, _dai_stream), plugin.py (device code and playback), constants.py, addon.xml.
- [10 0.4.5](https://github.com/matthuisman/slyguy.addons/blob/76d5a37e885f3943e0ec8eede05ee6ccefe8acef/slyguy.10play/slyguy.10play-0.4.5.zip): resources/lib/api.py (config, signed requests, device auth, live_channels, play), plugin.py (device code, HLS middleware, playback), constants.py, addon.xml.
- [ABC source snapshot d57a5390dd232aebdc47c2aef9d7a3365f2d769b](https://github.com/aussieaddons/plugin.video.abc_iview/tree/d57a5390dd232aebdc47c2aef9d7a3365f2d769b): resources/lib/comm.py, live.py, play.py, config.py, addon.xml and README. It is historic evidence, not a current supported account-login contract.

SBS discovers guide entries through its EPG API and resolves media IDs via the
playback API. Returned providers can be HLS or GoogleDAI. The inspected add-on
rewrites some HLS hosts/qualities and removes a DAI query field. AUTV must instead
preserve provider-issued URLs and comply with the approved ad/session contract.
The SBS source's lack of a Widevine exchange does not prove all SBS streams clear.

10 obtains an authenticated DAI session from a provider asset key; its live path
constructs a manifest URL from an events URL, and its optional VOD middleware probes
higher-quality variants. These transformations are not an AUTV implementation
recipe. The future connector needs the supported original stream and event/ad
lifecycle, including whether a platform DAI SDK is required.

7plus needs market/location handling and source-specific protection. Native
Media3 can support platform DRM, but CDM availability is not a valid licence grant.
ABC's historic client signs HLS authorization requests, uses response cookies and
can proxy-fix a playlist. Do not reuse those credentials/identities or introduce a
proxy from this evidence. Establish current supported account/playback access.

The inspected SlyGuy packages have empty reuse-licence metadata. No source code or
embedded signing keys are copied into AUTV. Their protocol evidence does not imply
current live endpoint availability or provider approval. This environment cannot
make live broadcaster API calls under its network policy. No additional real
7plus/10/SBS/ABC stream was played during this investigation.

## Shared QR connection-screen design and decision

Settings → Australian services → provider → Connect continues to use the retained
provider model. No separate QR polling job, backend, tracking URL or browser-cookie
import is added. Only 9Now has an enabled connector in this change.

On a wide TV the panel has a 224dp square black-on-white QR at left; at right it
shows the official HTTPS address, large readable manual code and expiry countdown.
Below it are waiting instructions, Open official sign-in and Cancel. On narrow
screens these sections stack and remain scrollable. Remote buttons are focusable;
the QR is descriptive accessible content, not a focus trap. The four-module quiet
zone is retained and bitmap sampling uses no smoothing.

ActivationQr uses ZXing core locally, with no network or disk writes. A reviewed
ActivationDestination controls the exact permitted URL. The current destination is
only https://www.9now.com.au/activate. Queries, fragments, credentials, lookalike
hosts, HTTP and arbitrary provider-returned URLs are rejected. No user code, device
code, access token, refresh token or private session is encoded. New destinations
require explicit review; the widget itself is independent of broadcaster playback.

The existing NineNowAdapter, Keystore AES-GCM session/pending-challenge storage,
expiry deadline, polling interval, retry/backoff and explicit cancel/sign-out are
reused unchanged. Application recreation restores the same unexpired challenge;
it does not issue a fresh code. At expiry, the QR and manual code disappear and the
website button disables; the retained model completes terminal cleanup. Explicit
cancel then Connect provides a new attempt. Rejection exposes the existing safe
error and allows a new user-initiated attempt. Approval removes the panel and shows
Connected even if channel discovery is empty or fails.

Future adapters need separate encrypted aliases/files, provider-scoped HTTP
credentials and device IDs, reviewed activation targets and independent state.
The existing Nine-specific model is not yet a multi-account provider registry.
Do not wire a new provider card to the Nine session store or claim its connection
from another broadcaster's success.

## Validation and remaining gates

- JVM round-trip decodes the locally generated QR and checks that only the official
  address is present; malicious and guessed-prefill URLs are rejected.
- Android UI tests decode pixels captured from the rendered QR, verify the manual
  alternative and expiry state, and use D-pad navigation/OK to reach Cancel.
- An integration fixture runs Settings → Nine Connect → QR → approved token →
  Connected with an empty catalogue, proving account connection and playback are
  separate. Existing lifecycle, retry, expiry, denial, encrypted-store and actual
  APK-upgrade fixtures remain in the CI suite.
- Fixture scans are not a physical-phone scan or live provider approval. The owner
  must scan the TV with a phone camera, enter the shown code on Nine, verify the
  saved connection through restart and update, and separately try each channel.
- No FAST playlist, public guide, channel number, six-hour refresh or broadcaster
  playback-resolution logic is changed by this QR work.

Permanent signing is a separate deployment prerequisite. The latest Native app
run stopped before compilation because Actions signing secrets were absent. The
setup script requires one trusted GitHub CLI session with secret/variable write
access; the connected tools cannot provision those. See [Android updates](../android-updates.md).
The signing setup/build additionally pins and checks the public certificate
fingerprint, preventing an accidental different backup from becoming the next key.
Do not claim permanent distribution is active until a signed workflow succeeds.

Recommended sequence: complete permanent signing setup; owner-retest the 9Now QR;
review SBS's permitted device/HLS/DAI path as the next connector; clarify ABC's
current account API; separately scope 7plus Widevine and 10 request-signing/DAI.
Keep the PR draft. Do not merge automatically.
