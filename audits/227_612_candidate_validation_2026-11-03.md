# AUTV 227 + 612 — discovered provider identities and validation gates

**Status: CANDIDATES ONLY.** No stream has passed a live AU network media-segment check or AUTV device playback. Do not update channel_guide.csv, selected lists or playlist.m3u based on this alone.

## 227 Rugby Pass TV
- iptv-org UK Pluto catalogue reference: https://github.com/iptv-org/iptv/blob/master/streams/uk_pluto.m3u
- Listed stream locator: https://jmp2.uk/plu-66f137cb483d460008ece053.m3u8
- Pluto channel ID inferred from redirect path: `66f137cb483d460008ece053` — confirm against current authorised Pluto UK provider catalogue, rather than trusting a third-party redirect.
- TVG identity: `RugbyPassTV.uk@SD`.
- Official World Rugby confirms RugbyPass TV FAST is carried on Samsung UK/US (not Australian availability): https://www.world.rugby/news/1001046/world-rugby-partners-with-samsung-tv-plus-to-accelerate-us-and-uk-expansion-with-rugbypass-tv-fast-channel
- Action: resolve this ID against fresh Pluto UK catalogue using normal authorised session flow. If official source available for AU, validate manifest and segment; then TV playback. No geo workarounds.

## 612 Whose Line Is It Anyway?
- iptv-org UK Pluto catalogue reference: https://github.com/iptv-org/iptv/blob/master/streams/uk_pluto.m3u
- Listed stream locator: https://jmp2.uk/plu-672cb91149c40600081cbee5.m3u8
- Pluto channel ID inferred from redirect path: `672cb91149c40600081cbee5`; confirm against fresh Pluto UK provider catalogue.
- Official US Pluto live channel listing: https://pluto.tv/us/watch/live-tv/32587/
- Action: attempt exact provider-ID match in UK catalogue and separate official US channel identity. If authorised AU playback is supported, validate HLS and actual device playback.

## Both channels — acceptance sequence
1. Confirm UK and US provider catalogue entry IDs, current names and regional rights.
2. Use current provider-issued URLs/session flow, not historical or expiring third-party redirect as a published primary.
3. Validate actual master/media manifest; verify nonempty segments and time continuity.
4. Tune on Australian AUTV device for at least 60 seconds; check audio, error messages, and EPG.
5. Only then replace playlist records. Preserve numbers 227 and 612 and any source provenance.
6. Return explicit failure at catalogue / auth / manifest / media segment / regional access / device stage, not generic 'No Live Stream'.

## 239 Rally TV — follow-on alternate-source investigation
- iptv-org UK Pluto catalogue ID: `696e024a7e92438c27a425a8`, redirect locator `https://jmp2.uk/plu-696e024a7e92438c27a425a8.m3u8`. Not validated for AU.
- Official WRC territory list https://www.wrc.com/en/misc/broadcast-guide lists free FAST distribution in European markets; AU not listed.
- Legal Australian alternative: Rally.TV standalone subscription (includes Rally 24/7), also DAZN AU: https://www.dazn.com/en-AU/help/articles/19002884413469-rally-tv .
- Investigate an authorised provider-supported integration or rights-cleared AU FAST distribution, not a restricted EU feed.
