# AUTV — EPG Gate 1 source recovery: first live evidence pass
**Scan:** 6 November 2026, 03:23 UTC  
**Mara Collins:** source and regional service validation; **Elliot Mercer:** tooling and regression evidence  
**Workflow:** https://github.com/safetyblade/autv/actions/runs/42156218440  
**Source artifact:** `autv-epg-gate1-source-candidates` (contains per-channel review CSV, summary, and complete P1 recovery queue)

## Results
- **55** published channels lacked an EPG channel record in the measured AUTV guide.
- All **8 established XMLTV feeds** were accessible for the read-only scan (Plex AU, Pluto US/GB/CA, Roku, Xumo, Tubi, Samsung).
- **26** channels have one or more **name-based candidate** IDs with real source programme events; **29** channels have no safe lexical candidate in the current sources.
- **10** channel identities have at least one exact-normalised-name candidate with current programme events. **None is an approved AUTV mapping yet**: naming equivalence does not prove the stream has the same regional timetable, programming rights or service variant.
- Candidate source rows may include multiple regions. The file provides source filename, XMLTV channel ID, source display name, match quality and observed now/future event counts.

## First identity-verification shortlist
| AUTV # | Channel | Example source | XMLTV ID | Review status |
| ---: | --- | --- | --- | --- |
| 307 | MyTime Movie Network | Roku | `be3c81de355c56f89cbf99301735cfd2` | Verify stream vs source programming |
| 330 | Universal Westerns | Roku | `8346cc0f3d415e39875fca80b67fd951` | Verify stream vs source programming |
| 501 | Bloodline Detectives | Pluto GB | `63d91cc58795f30008678e9d` | Confirm country/feed match |
| 759 | Xena Warrior Princess | Xumo | `99951412` | Confirm same live channel service |
| 776 | The Lone Ranger | Roku | `04526117859e505aac16c1b7b248894e` | Compare stream schedule |
| 778 | Lassie | Roku | `8c36c765b4415db78a85078c37df4a5f` | Compare stream schedule |
| 1112 | earthtouch | Samsung GB | `GBBD3200001E2` | Check current geographic variant |
| 1309 | Stingray Classic Rock | Samsung CA | `CABD1200036YT` | Check actual Samsung region variant |
| 1324 | Stingray Easy Listening | Samsung CA | `CABD1200030YP` | Check actual Samsung region variant |
| 1325 | Stingray SPA | Samsung CA | `CABD1200032N6` | Check actual Samsung region variant |

## High-profile no-match channels — alternative discovery
- **MUTV #216:** no matching channel ID in these eight source feeds. Manchester United documents a live **MUTV programme schedule in its app**, with reminder support; this is **not** evidence of a public XMLTV API or AUTV redistribution permission. Reference: https://www.manutd.com/en/help/faqs/frequently-asked-questions-for-mutv .
- **Comet TV #847:** no source record in the existing eight feeds; official Comet site has published channel information and third-party TV listings exist. References: https://comettv.com/ and https://www.tvinsider.com/network/Comet/schedule/ . These are reference schedules, not approved machine-readable imports.
- **CHARGE! #848:** no source record in the existing eight feeds; official site and TV listings are available at https://watchcharge.com/ and https://www.tvinsider.com/network/Charge/schedule/ . Region and stream equivalence need verification before use.
- None of these three should receive a synthetic `Now/Next` programme or a guessed schedule based on a similarly named FAST service.

## Next operations actions
1. Validate the **ten exact-name candidates** against each channel's current playback service and observed programme boundaries (and region); record evidence and reviewer before proposing a row in `epg_channel_crosswalk.csv`.
2. Review the **remaining 16 name-candidate channels** including multi-region variants; prefer a verified Australian source where possible, without substituting schedules from a different channel.
3. Investigate **29 no-candidate services** via official or authorised programme data, starting with MUTV, Comet and CHARGE!. Research availability and allowed use of machine-readable feeds; don't scrape programme sites into the app without permission.
4. After approved mapping changes, run playlist/EPG refresh, then measure the delta against **55 missing record** and full 24h coverage baseline. Stop and revert any mismatched programme.
5. Native 7plus/9Now/SBS EPG coverage is a separate adapter/device measurement exercise and is **not** represented by this FAST-only source audit.

**No programmes, stream sources, channel numbers, login sessions or logos were changed during this scan.**
