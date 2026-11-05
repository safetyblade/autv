# AUTV native ABC and Network 10 connection trials — 5 November 2026

This work is an **in-AUTV, debug-only, physical-TV experiment**. It does not install, start or redirect into another television application, import third-party credentials or create a fake account session.

## What the owner can test in the full debug APK

Open **Settings → Australian services → ABC iview** and choose **Check ABC live catalogue**. AUTV requests ABC's actual public iview home and live-collection metadata from the Android TV's current Australian internet connection. It displays the actual returned channel names/count, a safe HTTP failure or a response-schema failure. Successful discovery does **not** mean the user's ABC account has been linked, nor does it authorise media.

Open **Settings → Australian services → 10** and choose **Check 10 connection service**. AUTV contacts the actual 10 configuration endpoint and reports whether a recognised device-link contract is offered to an unregistered client. It does not invent an activation code or claim a session.

On the same 10 screen, select **Play 10 VIC native trial**. AUTV closes Settings and attempts a real HLS stream directly in the same Media3 television player/remote interface. This reuses an existing, *unofficial* experimental VIC-only source previously present in the isolated Media Lab. It is **not** an approved broadcaster integration, has no registered 10 session, no guaranteed rights or schedule, no account login, and must not be included as a normal production channel. The user must verify picture, audio, stream continuity and adverts. If playback fails, return to the guide to select an established provider.

The experimental stream is not added to the guide, favourites, recent watched history, home launcher or the accepted 7plus/9Now/SBS source paths. It is available only with the `BuildConfig.DEBUG` guard.

## Exact unsolved end-to-end linking requirement

- **ABC:** A real `/ctv/link` challenge requires a client registration/profile API key and a compatible `/auth/hls/sign` authorisation grant. Existing `AbcAdapter` and registered `AbcHttpAccess` already execute genuine login/poll/token when those inputs are provided. Without registration, neither debug probe nor the default APK can generate a genuine ABC TV activation code.
- **10:** The configured `TenHttpAccess` already performs real config, device-code, poll, catalogue and IMA/DAI playback. It requires actual registered request headers/signing and live channel grants. A public configuration response alone does not create a valid login or playback entitlement.
- The unofficial Kodi implementations show possible protocol approaches, but do **not** grant AUTV their client keys, signing capability or embedded identity. Do not copy signing material, impersonate other applications or remove advertisements/DRM.

## Regression and release gates

- Preserve all linked 9Now/7plus/SBS connections and the owner-verified Channel 7 adapter.
- No debug trial may report an account as Connected, store tokens, rewrite the real guide, interfere with existing provider identity, or become a production channel.
- JVM tests cover public-catalogue parsing, public 10 configuration recognition, honest no-activation reporting and redacted failures. Android UI regression verifies both debug test controls and the original unconfigured account guard.
- The debug build is for the owner's real-device TV tests. The main release remains unconfigured until AUTV-issued registrations and actual account/link/playback tests pass.
