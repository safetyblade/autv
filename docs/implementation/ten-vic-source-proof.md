# Network 10 VIC — live source proof (3 November 2026)

**Scope:** one channel only: Network 10 Melbourne (VIC). ABC and other 10 services excluded.

## Why AUTV currently cannot connect
The stock APK has empty `BuildConfig.TEN_REGISTRATION_SOURCE`. That prevents construction of `TenAdapter`, so Settings shows **Configuration required** before any network request or account login. PR #155 implemented injectable registered clients but supplied no live registration. The fact that the LG 10 app signs in does not provide AUTV with that application's session, device identity or media grants.

## Independent no-login live lead
The SlyGuy 10 Play Kodi add-on advertises unauthenticated live TV and IPTV guide integration: https://www.matthuisman.nz/2021/06/10-play-kodi-add-on.html . On 14–18 July 2026 the maintainer reported 10 VIC playback in Kodi/VLC and issued v0.4.5 for InputStream Adaptive compatibility:
https://forums.whirlpool.net.au/archive/91mxxqq8

**Probe candidate, NOT an approved AUTV source:** `https://i.mjh.nz/.r/10-vic.m3u8`. This is an unofficial redirect and must not silently become a production provider endpoint. Older unverified `live.10play.com.au/10VIC/stream.m3u8` listings must not be assumed current. AU-origin account-free availability and reuse permission are unproven.

A July 2026 failed installation reported inability to fetch AES-128 HLS keys from a Fastly key host and first MPEG-TS segments from a CBS/Paramount CDN. Other players succeeded. This gives a discriminating hypothesis: playlist acquisition alone does not prove key access, segment access or Media3 decoder compatibility.

## Read-only test on a legitimate Australian network

Run `python3 scripts/probe_ten_vic_hls.py` on a network in Victoria. Optional `--url` selects a single known public source if its host is allowlisted. The probe:
1. rejects non-HTTPS, credential-bearing, IP-literal and unlisted hosts; maximum five HTTPS redirects;
2. reports status, final host, variant codecs and MIME without signed queries/URLs;
3. requests a first variant manifest where present;
4. checks first AES-128 key **HEAD only** and first segment via bounded 1024-byte range GET; does not store key bytes;
5. never claims validated playback and does not bypass entitlement, geo or advertising.

Interpret HTTP 403/404/401, HEAD 405, redirects, missing keys, manifests and segment errors separately. Some CDNs do not support HEAD or range GET: those outcomes are *inconclusive*, not a blanket inaccessible-stream conclusion. Never publish signed stream URLs or session tokens.

### TV/phone playback proof

If the stream is lawfully usable in AUTV, create a **gated, debug-only Media3 10 VIC fixture** on the owner's device. No automatic injection into the released Australian TV category until:
- from AU/VIC IP, manifest, key and live media segments are accessible without borrowed identity or ad suppression;
- video/audio play on Android TV for **two continuous minutes** and survives live segment transitions;
- TV channel is the Victorian 10 service, EPG identity and rights/permission verified;
- current adverts/DAI and DRM handling remain intact;
- release source refresh survives token rotation and no user credentials are leaked;
- stock 9Now, Seven, SBS, FAST and Cast playback are unchanged.

If direct no-login playback is not permitted or fails, use the existing `TenHttpAccess` registered flow. The remaining dependencies are a Network 10-approved `TenClientRegistration`, request authoriser for the exact /api/ endpoints, proofChannelKey and per-channel grants. Get these through approved partnership/testing credentials; a normal 10 user account and an LG session alone do not constitute such a registration.

**Status:** This branch adds a diagnostic probe and tests only. No physical Australian playback, provider permission, login or APK was verified. Do not merge a fake 'Connected' state.
