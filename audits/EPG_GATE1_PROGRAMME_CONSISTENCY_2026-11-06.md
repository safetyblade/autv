# AUTV — EPG programme-consistency review, 6 November 2026

**Purpose:** Determine whether candidate EPG sources publish the same schedule at the same time before considering any approved source-to-channel mapping.

**Workflow:** [Cross-source comparison, successful run](https://github.com/safetyblade/autv/actions/runs/42159405274)  
**Evidence artifact:** `autv-epg-gate1-source-candidates` — `epg_candidate_schedule_consistency.csv` and `epg_candidate_schedule_consistency_summary.json`

## Measured result

- 143 programme-feed candidate pairs were compared over a 24-hour UTC window using 15-minute overlapping slots, only where both had actual programme titles.
- **23 pairs** from **different XMLTV source files** showed at least 90% agreement over at least two hours of shared programme time.
- **21 pairs** within one combined XMLTV source file showed at least 90% agreement; these are **not independent corroboration** and are separately marked.
- **82 pairs** had less than 50% agreement despite programme data overlapping, suggesting genuine regional or service schedule divergence.
- 14 pairs had partial agreement; 3 pairs lacked enough overlapping schedule.
- The six channel numbers with at least one strong cross-file pairing were **#413 Project Runway (Australia), #519 Unsolved Mysteries (Australia), #636 Anger Management (Australia), #776 The Lone Ranger, #778 Lassie and #1141**.
- This measures **feed-vs-feed schedule agreement only**. It does NOT verify the programme currently shown in the actual AUTV stream or the right to use/rebroadcast EPG data.

## Decision on the ten initial priority channels

| Channel | Identity/schedule review | Next action |
|---|---|---|
| #307 MyTime Movie Network | Roku and Samsung country variants have significant schedule conflicts; some Samsung regions agree with others | **Hold** until current AU stream's actual programme time is compared with the correct regional schedule |
| #330 Universal Westerns | Xumo candidate's channel ID differs from `ads.xumo_channelId`; Roku is an unproven second service | Confirm the provider ID mapping and observe playback |
| #501 Bloodline Detectives | **Blocked by active identity hold #186:** stream path encodes Medical Detectives | Do not map until stream identity confirmed |
| #759 Xena Warrior Princess | Stream's Xumo ID differs from Xumo XMLTV candidate ID | Confirm exact regional catalogue identity |
| #776 The Lone Ranger | Roku and Pluto US schedules agree on 54 overlapping 15-minute slots (100% within overlap), but stream uses a distinct Xumo channel ID | Good **device verification candidate**; not yet an approved crosswalk |
| #778 Lassie | Roku and Samsung US schedules agree on 23 overlapping slots (100%); stream uses distinct Xumo channel ID | Good **device verification candidate**; not yet an approved crosswalk |
| #1112 earthtouch | Samsung GB candidate exists while stream advertises AU | Confirm AU regional service/schedule |
| #1309 Stingray Classic Rock | Samsung US/CA agree, but Plex AU shows substantially different titles | Verify distributor schedule identity before mapping |
| #1324 Stingray Easy Listening | Samsung US/CA agree; Plex AU conflicts | Verify distributor schedule identity |
| #1325 Stingray SPA | Agreement inside Samsung's multi-region XMLTV only | Source region/stream verification needed |

## Immediate practical acceptance checklist

Mara should validate on AUTV at the same timestamp as candidate XMLTV:
1. Select channel and capture visible programme and/or broadcast channel branding.
2. Record exact UTC time and provider/region, preferably two programme boundaries at least 30 minutes apart.
3. Compare observed programme titles and start/stop times to the candidate XMLTV row. Ignore generic placeholders like `Live`, `Movie`, `Music` that cannot identify the schedule.
4. Confirm source origin and access rights; if a candidate is verified, propose **one** reviewed alias row in `epg_channel_crosswalk.csv` with approval and rationale, then rerun the EPG build and quality audit.
5. Mark wrong-region variants as rejected in the audit record, not as fallback schedules.

**No channels, playlists, numbering, logos, provider accounts or live published programme entries changed in this investigation.**
