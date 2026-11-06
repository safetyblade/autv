# Network 10 — optional registered integration

**Current viewing path:** AUTV offers **10 VIC (#10), 10 Drama (#11) and 10 Comedy (#12)** as tested direct HLS channels under Australian TV, **without signing into Network 10**. They are external maintained sources; physical playback approval is not a broadcaster distribution licence.

The native Network 10 registered-client adapter remains in code, but there is **no working 10 account activation link in the normal Settings flow**. The earlier test request to `https://10.com.au/api/v1/config` returned HTTP 404, and that obsolete endpoint must not be represented as a valid sign-in option. Do not conflate a no-login FAST/DAI manifest with a logged-in provider session.

## Registered-client integration (future)

An approved `TenRegistrationSource` can supply `TenProviderConfiguration(registration,proofChannelKey,daiNetworkCode)`. Example: `-PautvTenProviderSources=/approved/ten-source -PautvTenRegistrationSource=approved.package.TenSource`.

- Obtain current broadcaster-authorised API origins, device-code and session parameters, request-signing service and explicit grant list for VIC channels.
- Use existing descriptor resolution and Media3 IMA ad insertion where applicable. Preserve signed query/session material, advertising and publisher restrictions; do not manufacture authoriser headers.
- Treat free-to-air linear channels and Australia-specific Pluto-powered FAST channels as **distinct source/rights products**. A consumer 10 login does not automatically authorise AUTV to extract all 10 app's private FAST playback endpoints.
- Use [source discovery](../SOURCE_DISCOVERY.md) and issue #172 for future free-channel research; a missing international Pluto ID never proves a programme is unavailable on 10 Australia.

Retain the existing registered-client transport for a future authorised implementation; do not add a fake Connect button.
