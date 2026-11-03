# AUTV — maintenance reference

> For maintainers, not the household TV user guide. Never publish credentials, private media authorisation tokens or restricted provider interfaces here.

## Sources of truth

- `channel_guide.csv` — configured curated FAST/IPTV channel numbers, names, genres, EPG expectation, primary/alternate source, status and description.
- `playlist.m3u` — generated/published stream playlist.
- `epg.xml.gz` — generated/published programme guide.
- Native broadcaster integration — **separate app-side catalogue**. Its current Australian TV channels and connection states must be exported/validated against the owner-approved build before claiming full catalogue coverage in a consumer guide.

## Refresh

From GitHub Actions open **Refresh playlist → Run workflow** when the workflow is available and approved. After a successful publication, reload/refresh the guide in AUTV and check recent changes on an Australian device.

Do not treat source catalogue availability, valid manifests, EPG metadata or HTTP success alone as playback proof. Test actual segments, device playback and applicable geo/DRM/advertising/authorisation requirements. Do not promote discovery-only backup feeds to production without verification.

## Editorial policy

Keep a stable logical channel number. Avoid duplicated services. Preserve a known-good primary over speculative alternates. Investigate viable authorised replacements before retiring an existing channel. Remove retired guide entries only via approved source changes, not as part of a documentation refresh.

## Docs release check

1. Capture the current commit and build identifier.
2. Reconcile active CSV rows with the published M3U (record differences).
3. Reconcile Australian TV native catalogue and provider states separately.
4. Regenerate the Word/Markdown directory from current approved data.
5. Verify instructions on the owner-approved Android TV device, especially Settings, provider linking, QR placement, startup channel and favourites.
6. Open a documentation-only PR, with engineering verification and product approval.

Historical streaming investigations, prior build failures and provider debugging belong in GitHub issues/PRs rather than user-facing documentation.
