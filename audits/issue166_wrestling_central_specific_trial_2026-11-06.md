# Wrestling Central specific source trial — 6 November 2026

Issue: #166 | PR: #169 | [Successful diagnostic CI run #42127249674](https://github.com/safetyblade/autv/actions/runs/42127249674)

## Outcome: NOT_LISTED_NO_HLS_CANDIDATE

This is a **failed channel discovery**, not a successful playback test. The dedicated CI run inspected known official Roku watch identity `439096119777e0f33894343b551bece4`, matched source names and exact identity against current public Roku, Tubi, Xumo, Plex AU, Pluto US/CA/UK listings, and attempted an ordinary read of the official Roku watch page.

| Test | Observed |
| --- | --- |
| Official Roku watch URL | HTTP 200, but returned HTML did **not** contain "Wrestling Central" |
| Roku catalogue | 0 matches |
| Tubi catalogue | 0 matches |
| Xumo catalogue | 0 matches |
| Plex AU catalogue | 0 matches |
| Pluto US/CA/UK | 0 matches |
| Independently published HLS media candidates | 0 |
| Media-segment validation | Not possible (no HLS candidate found) |
| Australian device validation | Not attempted (no source to tune) |
| Production channels and streams | Unmodified |

## Current-service warning

- The NWA site still carries an older Roku promotion for 24/7 Wrestling Central, originally announced as exclusive to the US and Canada. This is an historical listing, **not confirmation of current active video**.
- SEScoops reported on 20 October 2026 (updated 26 October) that Wrestling Central was being shut down; article notes the shutdown had not been publicly confirmed by Roku or the broadcaster. Cite: https://www.sescoops.com/article/skydance-wow-wrestling-central-roku-channel-shut
- Official historical page: https://www.nationalwrestlingalliance.com/watch
- **Decision:** do not add channel to AUTV, do not invent stream URL, do not reuse the previous withdrawn #297 trial as an active channel. Review only if publisher later publishes renewed service or a genuinely supported Australian source.

NWA Powerrr can be investigated separately via its officially published Tubi on-demand offering or Comet live schedule, but **on-demand programming is not equivalent to the lost 24/7 Wrestling Central linear channel**.

No existing AEW/TNA/NXT primary sources, fallbacks, numbering, playlist entries, or player functionality are changed.
