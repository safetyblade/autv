# AUTV #166 — candidate source trial results (6 November 2026)

Read-only GitHub runner execution: [run 42126130323](https://github.com/safetyblade/autv/actions/runs/42126130323) · [draft PR #168](https://github.com/safetyblade/autv/pull/168).

**Existing AUTV primaries remain unchanged.** The separate source candidates below must not be automatically inserted into `playlist.m3u`, `custom.m3u`, channel selectors, or app playback until owner-approved Australian-device testing and distribution policy checks are complete.

| Existing channel | Candidate source | Channel/provider identity | GitHub-runner media segment | Stage |
| --- | --- | --- | --- | --- |
| #200 Watch AEW | Roku | `8346c21c4b5b032cca68a74933e7642b` | Reachable | **Fallback candidate only** |
| #201 CW Presents WWE NXT | Roku | `ac0caef9123a35df3b884b139340a597` | Reachable | **Fallback candidate only** |
| #202 TNA Wrestling Channel | Roku | `6d8659091f745b8b864f438f06c56fae` | Reachable | **Fallback candidate only** |
| #202 TNA Wrestling Channel | Xumo | `99991341` | Reachable | **Fallback candidate only** |
| #617 Comedy Dynamics | Tubi | `400000169` | Reachable | **Fallback candidate only** |
| #622 Just For Laughs Gags | Xumo | `99991204` | Reachable | **Fallback candidate only** |

Six distinct non-primary candidates passed short HLS manifest/media-segment fetches in a GitHub Actions runner. None has a confirmed 60-second Australian TV playback, entitlement or ads verification. Provider-supplied URLs can be temporary or geography-specific. The workflow intentionally does not save signed URLs, JWTs or session parameters in this report.

## Remaining requested trials

| Target | Source discovery outcome | Next stage |
| --- | --- | --- |
| #612 Whose Line Is It Anyway? | No distinct alternative in checked Roku/Tubi/Xumo/Plex/Pluto catalogues | Inspect fresh provider-region Pluto identities; preserve current primary |
| Wrestling Central (NWA / WOW) | No exact playlist match | Official Roku 208 channel exists in US/CA; requires supported AU playback source or authorised service integration |
| FITE by Triller 24/7 | No exact playlist match | Official US/Canada Roku FAST listing; provider session or new published AU feed required |
| ESPN The Ocho | No exact playlist match | Roku placement advertises service; seek independent stable AU-carried FAST identity |
| ESPN / ESPN2 | Not scanned for unauthenticated streams | Australian subscription/provider linking (Disney+ or Kayo) rather than bypassing login |

## Approval and fallback policy

1. **Primary never replaced while present and playable.** If the primary disappears or actually fails at playback, offer an approved alternate for the **same logical channel**, not another directory entry.
2. The existing `restore_missing_curated.py` preserves published entries; it restores missing ones only. Do not treat mere successful HTTP 200 as primary failure.
3. A GitHub runner technical check is **not** an Australian device acceptance, provider permission, or proof that advertising and DRM work.
4. All new channel identities require separate approval and unique numbering only after a verified authorised playback path.
5. Keep MUTV #216 and its successful Brightcove/Fastly source untouched.

For re-test, run the workflow manually or through PR and inspect the `issue166-source-trials` artifact. It checks up to 30 distinct alternatives without writing production state.
