# Australian broadcaster readiness — AUTV

This is an operator checklist, not an approval, registration or proof of playback. Source authority: `docs/providers/abc-registration.md`, `docs/providers/ten-registration.md` and issue #140.

## Current default-build behaviour

| Service | Shipped code | Default build | Owner acceptance |
|---|---|---|---|
| 9Now | Native account, catalogue and playback | Available | Connected on TV; verify an actual channel |
| SBS On Demand | Native integration | Available | Preserve existing working implementation |
| 7plus | Native integration | Available | Connected, 53 channels observed on TV; protect owner-verified Channel 7 playback |
| ABC iview | Registered adapter, regional live catalogue and authorised HLS handoff | **Configuration required** | Not linked or playing |
| Network 10 | Registered adapter, live grants and descriptor/IMA handoff | **Configuration required** | Not linked or playing |

Do not interpret a Connected label, provider catalogue, QR screen, build success or synthetic test as proof of real playback.

## Provisioning required before an authorised distribution APK

**ABC:** ABC-issued AUTV `productId`, `profileApiKey`, supported profile/UID grant, approved media signing/authoriser for the exact HLS request, approved main `houseNumber` and channel grants, permitted distribution/region. Account linking alone does not provide a signed media grant.

**10:** Network 10-issued AUTV client/configuration, accepted request headers/fields, approved GET signing operation, permitted origins, device/activation flow, authorised live `row.key` identities, the discovered main proof key, negotiated live format and any required DAI network code. A device token does not provide a live-video grant.

Provider-issued signing secrets, account credentials, approval tokens, media query signatures and borrowed identities must not be committed, logged or placed in public GitHub Actions secrets for the debug default distribution. Use the existing approved `-PautvProviderSources` and `-PautvApplicationClass` extension only with authorised source and signing access.

## Verification before marking a service complete

1. Confirm authorised registration is provisioned for AUTV and the selected Australian region.
2. Confirm a fresh device-code challenge opens the official activation address and survives application restart.
3. Confirm approved account linking, refresh, disconnect and isolated encrypted state.
4. Confirm granted channel identity and regional catalogue refresh; revoke removed grants.
5. Confirm media authorisation/descriptor requests preserve issued signatures, DRM and advertising requirements.
6. On the physical Android TV, tune a granted channel and verify video and audio, ad/DRM behaviour where applicable, expiry/retry and reconnection.
7. Record the tested APK commit, timestamp, service, permitted region, channel and result, without secret identifiers or URLs.

## Native-channel EPG

The guide can display provider-supplied `ProviderChannel.programmes` and will show **Programme details unavailable** when those data are absent. In particular, 7plus live-channel discovery does not establish that AUTV has an authorised channel schedule. Do not populate a provider row by guessing programmes from a similarly named public TV station or synthesising airtimes. First identify a provider-approved schedule source, map its stable channel IDs and region, then add fixture tests for correct and missing schedules without modifying the owner-verified 7plus playback adapter.

## Build and QA boundaries

- Default application class `dev.prestwich.autv.AuTvApplication` must remain fail-closed for ABC and Ten.
- Existing settings screen appearance and TV D-pad behaviour are owner-accepted; limit further changes to evidenced defects.
- The Seven provider baseline guard and its auth/playback code are out of scope.
- `validation/issue140` is the single integration branch for this work; no release or merge until mandatory CI and device checks pass.
- FAST-channel feed discovery/recovery belongs to a separate workstream.
