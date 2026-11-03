# AUTV Markdown documentation inventory — 3 November 2026

Issue #151 | PR #152 | Proposed disposition, not deletion authority

**Principle:** the root README and home TV guide should contain only instructions for people who install and use the app. Engineering, feed recovery, provider authorisation, tests and investigations are retained outside the user pathway. Historical notes remain available in git history and issues, but are not presented as current instructions.

| Existing document on main | Decision | Reason / required action |
| --- | --- | --- |
| `README.md` | **Rewrite (done in PR)** | Product quick start only |
| `TV_GUIDE.md` | **Regenerate (done for FAST; Australian TV pending)** | Current numbered directory, setup and clear provider caveat |
| `CHANNEL_GUIDE.md` | **Keep for maintainers (updated)** | Editorial taxonomy; no longer 643 |
| `CHANNEL_QA.md` | Keep in maintenance | Channel test records; review stale status before quoting |
| `native/README.md` | Keep in maintenance, later shorten | Long implementation notes and historic build documentation; not user landing |
| `native/CAST-PLAYBACK.md` | Keep in maintenance | Cast engineering validation |
| `native/DUAL-TV-GUIDE.md` | Keep in maintenance | Guide behaviour design |
| `native/FAVOURITES-VERIFICATION.md` | Keep in maintenance | Evidence and regressions |
| `native/PLATFORM-FEATURES.md` | Keep in maintenance | Platform implementation/limitations |
| `android/CREATOR-PLAYBACK-FEASIBILITY.md` | Archive as engineering research after review | Not household instructions |
| `docs/android-updates.md` | Keep in maintenance | Update signing/release instructions; verify latest |
| `docs/australian-provider-assets.md` | Keep in maintenance | Brand/asset governance |
| `docs/implementation/australian-provider-app-integration.md` | Keep engineering reference | Native provider architecture |
| `docs/implementation/cast-media-handoff.md` | Keep engineering reference | Cast handoff |
| `docs/implementation/provider-media3.md` | Keep engineering reference | Media3 implementation |
| `docs/providers/abc-registration.md` | Keep restricted-to-maintainers instructions | Do not include credentials in docs; provider setup currently unverified |
| `docs/providers/ten-registration.md` | Keep restricted-to-maintainers instructions | Provisioning steps, no credentials |
| `docs/spikes/9now-native-prototype.md` | Archive as historical engineering spike after review | Prototype history |
| `docs/spikes/9now-seinfeld-native-connector.md` | Archive as historical engineering spike after review | Prior investigation |
| `docs/spikes/abc-iview-native-investigation.md` | Archive as historical engineering spike after review | Prior investigation |
| `docs/spikes/australian-provider-activation.md` | Archive as historical engineering spike after review | Prior exploration |
| `docs/spikes/australian-provider-implementation-program.md` | Archive as historical engineering spike after review | Prior programme |
| `docs/spikes/network-ten-native-investigation.md` | Archive as historical engineering spike after review | Prior investigation |
| `docs/spikes/ten-abc-seven-native-decision.md` | Archive as historical engineering spike after review | Historic decision; current implementation authoritative |
| `docs/startup-channel.md` | Keep in maintenance | Current startup settings and fallback contract; validate device UI |
| `audits/227_612_candidate_validation_2026-11-03.md` | Keep as dated operational evidence | Not part of household documentation |
| `audits/36_channel_replacement_source_discovery_2026-11-03.md` | Keep as dated operational evidence | Not part of household documentation |
| `.Jules/palette.md` | Keep internal agent reference | Not household docs |
| `.jules/bolt.md` | Keep internal agent reference | Not household docs |
| `.jules/sentinel.md` | Keep internal agent reference | Not household docs |

## Applied in PR #152
- Home-facing entrypoints are `README.md` and `TV_GUIDE.md` only.
- Engineering maintenance reference is `docs/MAINTENANCE.md`.
- Word export and workflow run separately from playback implementation.
- No existing engineering files have been deleted, moved or modified beyond correcting `CHANNEL_GUIDE.md` totals.

## Pending explicit final approval
- Decide whether to move archival spikes into `docs/archive/` or retain in place. No mass deletion proposed.
- Verify the latest installed app and export broadcaster channels for the Word and Markdown guides.
- Record final document version and approved downloadable release location.
