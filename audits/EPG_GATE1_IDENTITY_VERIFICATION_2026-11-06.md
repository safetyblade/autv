# AUTV EPG identity verification — 6 November 2026

**Workflow:** https://github.com/safetyblade/autv/actions/runs/42157773462 (passed)
**Artifact:** `autv-epg-gate1-source-candidates` — `epg_candidate_identity_triage.csv`
**Scope:** Read-only comparison of published playlist stream identity clues with proposed EPG source IDs. No EPG maps were approved.

## Results

- **117** candidate review rows (several channels have multiple regional/source options).
- **29** channels still have no plausible name-based candidate.
- **80** candidate rows remain unverified, without a definitive same-service identity.
- **6** Xumo XMLTV candidate rows disagree with the stream's explicit `ads.xumo_channelId`. These may be different cataloguing schemes or genuinely different services; do not infer equivalence.
- **2** rows for channel **#501 Bloodline Detectives** are blocked because the published Wurl playlist path decodes to `Samsung-gb_MedicalDetectives_HLS` — a different service title.

## Specific investigation list

| AUTV # | Published name | Stream identity clue | Candidate EPG | Disposition |
|---:|---|---|---|---|
| **501** | Bloodline Detectives | `Samsung-gb_MedicalDetectives_HLS` | Pluto GB Bloodline Detectives / Samsung GB Bloodline Detectives | **BLOCKED: likely mismatched source** |
| 330 | Universal Westerns | Xumo stream channel `99984051` | Xumo XMLTV `99951255` | Verify actual service |
| 335 | The Asylum (Australia) | Xumo `89009005` | Xumo XMLTV `99991331` | Verify actual service |
| 519 | Unsolved Mysteries (Australia) | Xumo `89809006` | Xumo XMLTV `99991680` | Verify actual service |
| 759 | Xena Warrior Princess | Xumo `99984056` | Xumo XMLTV `99951412` | Verify actual service |
| 776 | The Lone Ranger | Xumo `99984052` | Xumo XMLTV `99951258` | Verify actual service |
| 778 | Lassie | Xumo `99984050` | Xumo XMLTV `99951259` | Verify actual service |

**Do not correct #501's stream source, rename its channel, or remove it without owner approval.** Check the actual playback identity and search for an approved Bloodline Detectives stream or corrected channel label. The discrepancy exists in playlist metadata/source identity, not just EPG matching.

## Programme matching next

1. Mara investigates #501 and confirms which channel actually plays. Mark this as priority quality assurance and prevent attaching any Bloodline Detectives XMLTV to the currently configured source.
2. For Xumo ID mismatches, obtain a provider-returned link between the stream's Xumo channel ID and the XMLTV channel ID, or compare verified actual programme boundaries for the same territory. Title match alone does not suffice.
3. Prioritise candidates where the stream source ID and XMLTV candidate ID align exactly, then compare regional programming.
4. Make approved changes exclusively through `epg_channel_crosswalk.csv`, each with named approval/evidence. No automatic approval is performed by this code.
5. Audit now/next and next-24-hour coverage following verified changes.

**Technical limits:** A playlist-path hint is strong evidence for triage, but is not itself a live playback test; a different Xumo numeric ID may reflect a distribution alias and does not alone prove different programmes.
