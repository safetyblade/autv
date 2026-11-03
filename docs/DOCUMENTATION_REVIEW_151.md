# AUTV documentation clean-up report — draft review (3 November 2026)

Issue: #151 · Draft PR: #152 · Owner: Mara Collins · Engineering review: Elliot Mercer · Product approval: Helena

## Confirmed and corrected

| File | Before | Action | Outcome |
| --- | --- | --- | --- |
| `README.md` | Mixed home instructions, outdated v0.4.0, research rules and playlist/EPG maintenance | Replaced with clear product/install/navigation landing page | Updated on docs branch |
| `TV_GUIDE.md` | Dated 5 Oct 2026, claimed 643 and included historical channel narrative | Regenerated numbered FAST/IPTV directory from master CSV | 636 active FAST channels on docs branch |
| `CHANNEL_GUIDE.md` | Claimed 643, old category totals | Corrected dated category snapshot | Updated on docs branch |
| `docs/MAINTENANCE.md` | Source workflow notes scattered through home-facing README | Added dedicated maintenance reference | New on docs branch |
| `scripts/build_tv_guide.py` | No verified repeatable Word-export process in this task scope | Added Word guide exporter with optional verified Australian TV CSV | New on docs branch |
| `.github/workflows/docs-guide.yml` | No dedicated Word artifact in this task scope | Added PR/workflow-dispatch build for a review artifact | New on docs branch |

## Data reconciliation

- Current `channel_guide.csv`: **636** active configured entries; no duplicate numbers.
- Current `playlist.m3u`: **636** entries, **636** unique assigned numbers.
- CSV/playlist number mismatch: **0**. These counts do not establish whether live streams play or have current programmes.
- Australian TV native-provider catalogue: **not represented by the FAST CSV and not yet exported/verified**.
- The old 643-channel document count is obsolete; do not treat all seven removals as new product changes from this PR.

## Awaiting review/verification

1. Obtain the complete native Australian TV channel export for ABC, Seven, Nine, Ten and SBS from the latest approved APK. Confirm actual numbers, names, providers, visibility and active/playback status. Keep blocked services identified.
2. Run Word guide workflow with the verified export; inspect every page, pagination and layout before distributing.
3. Elliot to check installation, remote focus/navigation, QR and Settings instructions against the latest TV build.
4. Continue the repo-wide Markdown inventory to identify other obsolete docs without deleting source-of-truth or engineering files.
5. Helena to sign off after proof that Word, Markdown and the on-device channel catalogue agree.

No app code, stream URL, source ordering, playlist, EPG or broadcaster integration has been changed in this documentation PR. Retain issue and PR history for audit.
