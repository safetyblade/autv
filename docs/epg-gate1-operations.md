# AUTV — EPG Gate 1 operating procedure

**Owner:** Mara Collins — Channel Operations & Support  
**Engineering:** Elliot Mercer  
**Current checkpoint:** 6 November 2026

## What the guide measures

The daily **EPG Gate 1 — real schedule coverage audit** reports the state of published FAST/IPTV programme information, separately from stream availability. The source is `epg.xml.gz`, not the aspirational `EPG` column in `channel_guide.csv`. The audit writes three files as one workflow artifact:

- `epg_gate1_summary.json`: current/next and next-24-hour coverage
- `epg_gate1_channel_audit.csv`: evidence per numbered FAST channel
- `epg_gate1_recovery_queue.csv`: prioritised actionable exceptions

The playlist refresh builds `audits/epg_gate1_match_trace.csv`, identifying each published channel's exact-ID, approved mapping, unique-name fallback, ambiguous-name, or missing-source outcome with upstream source and programme count.

## Start here

Review `audits/epg_gate1_recovery_queue.csv` in priority order. The initial gate run of 6 November (03:09 UTC) found **416** published channel exceptions: **55** with no XMLTV channel record, **155** with no current programme and **206** with less than 80% forward-24-hour coverage. These counts change as upstream EPG snapshots advance; never copy them as permanent metadata. A channel can still play even if no EPG exists.

## Safely correct missing guide information

1. **No XMLTV channel record (priority 1).** Check whether a legal, correctly regioned upstream EPG genuinely describes the same programme service. Find its exact XMLTV ID and the source filename. MUTV #216, Comet TV #847, and CHARGE! #848 are initial examples needing evidence; their names alone do not establish a match.
2. **No current programme (priority 2).** Inspect the upstream feed's schedule window, timezone, invalid events and acquisition time. A correctly matched channel can still have stale schedules. A crosswalk alone does not fix freshness.
3. **Insufficient next-day coverage (priority 3).** Investigate duration, forward horizon, time overlaps, and whether the source offers an approved more complete schedule. Do not synthesise programmes.
4. **Approve each identity correction.** Add a row to `epg_channel_crosswalk.csv` with `target_tvg_id,source_tvg_id,source_file,approved_by,reason`. Use the exact upstream source filename from `scripts/build_epg.py`. The mapping fails validation if its target or source is unknown, duplicate, unapproved or missing actual source programmes.
5. **Measure again.** The next playlist refresh preserves existing working streams and channels and publishes the updated EPG with a source trace. Compare the new daily audit with the prior checkpoint to confirm genuine now/next improvement.

Do not take schedule details from similarly named regional FAST channels without verifying that they are the same service. No provider credentials, playback links, or account sessions belong in the registry.

## Free-To-Air TV distinction

The FAST audit does not measure live provider-native programmes from SBS, Nine or Seven. Their programmes are supplied by their individual adapters, so collect a separate connection/session-safe report of **provider channel ID, EPG now/next availability and programme horizon** from the app. Preserve the internal provider identities and existing negative persisted native numbers until a global country-aware number registry is approved.

The full guide displays **Free-To-Air TV** as its consumer-facing category label. The application retains the existing internal `Australian TV` genre so integrations, favourites and tuning are not rewritten.

## Deferred checkpoints

- **Gate 2 logos:** controlled channel artwork registry keyed by stable `provider+channelId`, with approved overrides, provider logo fallback, checked caching and AUTV placeholder. MUTV and 7AFL are initial inspection targets.
- **FTA numbering:** SBS 20 + Nine 34 + Seven 54 = 108 channels even before ABC or Network 10. Any proposed allocation restricted to 1–99 is insufficient. Reserve a much larger collision-free region and separate permanent identity from numeric display before making changes.

**No channels are removed, renumbered or substituted through this EPG work package.**
