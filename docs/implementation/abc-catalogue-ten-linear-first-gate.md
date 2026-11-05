# AUTV — ABC catalogue recovery and staged 10 linear guide (5 November 2026)

## Evidence from real ABC public API
A read-only GitHub-hosted probe against `https://api.iview.abc.net.au/v2/home` returned HTTP 200 on 5 Nov 2026.

The live collection's actual title is **Watch ABC Live**, ID **959**, `type=livestream`. The old parser required **Watch ABC Channels Live** and rejected the response before loading the collection.

The live collection returned **17** `items` with `type=livestream`, valid alphanumeric `houseNumber` IDs and first-party relative `/video/{id}` self links. Verified metadata includes:
- ABC NEWS: `NS1413V001S00`
- ABC TV VIC: `LS1807V001S00`
- ABC Kids: `LS1604H001S00`
- ABC Entertains: `LS1603H001S00`
- ABC Family: `LS1602H001S00`
- State variations NSW, QLD, WA, SA, NT, TAS, ACT and ABC's additional thematic live catalogue entries.

The collection fix recognises the actual typed title and historical title and does not invent channel metadata. Guide display removes redundant ` Live Stream` suffix while preserving original house IDs for favourites, refresh and media requests. The existing linked ABC session remains in encrypted storage; the public catalogue request does not require its token.

**Important:** experimental ABC media authorisation in `AbcExperimentalLinkAccess` still deliberately rejects playback. Guide discovery success is not proof that ABC can play; media authorisation remains an independent acceptance gate.

## Network 10 — initial four linear services only
The owner's real-device screenshots demonstrated 10 VIC **picture** through the existing debug playback trial. No confirmed audio, two-minute continuity, ad transitions, programme guide, signed account entitlement or source-use permission yet.

The DEBUG-only Australian TV guide now includes four distinct source trials:
- **10 VIC** — `https://i.mjh.nz/.r/10-vic.m3u8`, picture observed by owner.
- **10 Drama** — historical `https://i.mjh.nz/10bold-vic.m3u8` alias; **not verified**, could be outdated.
- **10 Comedy** — historical `https://i.mjh.nz/10peach-vic.m3u8` alias; **not verified**, could be outdated.
- **Nickelodeon** — historical `https://i.mjh.nz/10shake-vic.m3u8` alias; **not verified**, actual channel identity may differ.

All sources above are third-party public candidates; do not promote them to production without verifying current original media origin, authorisation for AUTV use, branding, EPG, advertising and independent device audio/video. The stock 10 registered adapter still requires approved broadcaster registration and cannot sign in via the obsolete hardcoded `/api/v1/config` (HTTP 404 in owner test). No misleading Connected state is introduced.

The trial rows use isolated negative test numbers and identifiable labels, do not appear in release builds, and are excluded from startup-channel selection and watched history. Actual 10 channels should eventually receive assigned logical numbers only after verification. The existing Nine, Seven, SBS and FAST source implementations are unchanged.

## Testing in full AUTV debug APK
1. Install the APK from the current PR's green **AUTV Full Application Debug** workflow. Try installing as an update first. Android preserves the linked ABC account only when the new APK uses the same signing certificate. CI debug runners may use different debug keys; if Android reports UPDATE_INCOMPATIBLE, do not claim the session can be preserved. An uninstall clears app data and would require re-linking ABC. Record the installation outcome.
2. Open **Settings > Australian services > ABC** and select **Refresh channels**. Expect discovered ABC channels, no generic ABC-CATALOGUE error. Verify linked state is retained, 17 public live rows available (the number may change as ABC updates its catalogue), ABC TV VIC specifically present. An ABC tune may remain blocked at media authorisation — log this separately.
3. Open **Australian TV** in the guide. Expect the four clearly named, experimental 10 rows, with 10 VIC already picture-observed; test each channel's video, audio, two-minute play and programme branding on Australian internet.
4. Capture exact failed source name, Media3 error code and whether manifest/key/segment requests were available. Do not print signed session URLs. A 404 or wrong-branded legacy source must be retired from the trial rather than relabelled as working.
5. Confirm 9Now, 7plus, SBS and FAST playback and favourites unchanged.
6. Keep this PR unmerged until physical source acceptance and production permission review. **No full 10 FAST catalogue is in scope.**


## Updated 6 November findings and next live-player gate

Maintained Melbourne `https://i.mjh.nz/au/Melbourne/raw.m3u8` was fetched successfully in GitHub Actions run **42120807622**. It lists 10, 10 Drama, 10 Comedy and Nickelodeon. Direct source checks showed:
- 10 VIC `/.r/10-vic.m3u8`: HLS 200 via `dai.google.com`; owner physically observed picture.
- 10 Drama: old `/10bold-vic.m3u8` 404, corrected `/.r/10bold-vic.m3u8` HLS 200 via Google DAI.
- 10 Comedy: old `/10peach-vic.m3u8` 404, corrected `/.r/10peach-vic.m3u8` HLS 200 via Google DAI.
- Nickelodeon: old `/10shake-vic.m3u8` 404; corrected alias reaches CloudFront but 403; removed from playable trial pending separate investigation.

**User approved 10 VIC as always-on Australian TV.** The full AUTV build now inserts channel number **10** with stable `autv.10-vic-live` identity; channel is not a linked 10 subscription and its third-party public source is not evidence of official distribution permission. Two corrected source trials for Drama and Comedy are debug-only until picture, audio, programme identity and ads are tested. Do not silently re-add failed Nickelodeon.

ABC public video metadata `/v2/video/LS1807V001S00` returns `requiresLogin=false`, `playable=false`, and **no** `_embedded.playlist`: the prior ABC media resolver is looking for a media contract not supplied by that endpoint. However three public HLS redirects—ABC VIC, NEWS and Kids—each return HTTP 200 HLS at ABC's Akamai media infrastructure in run **42121123028**. Separate DEBUG guide source trials have been added to establish physical Media3 playback without changing or relying on the saved ABC login. These third-party redirects are not cleared as production integration. Do not bypass stream rights, geofencing, ads or DRM.

The read-only catalogue diagnostic grew beyond 256 KiB, so its response limit is now 2 MiB, matching the full application fetcher. The real `v2/home` live collection remains subject to schema checks.
