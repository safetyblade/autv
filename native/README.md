# AU TV Native

This directory is an additive client layer for the existing AU TV service.

## Safety boundary

The existing service remains authoritative and untouched:

- `playlist.m3u` remains the Live Channels / TVirl playlist endpoint.
- `epg.xml.gz` remains the XMLTV endpoint.
- `channel_guide.csv` remains the editorial source of truth.
- the existing refresh workflow and source-selection scripts remain unchanged.

The native work consumes those outputs. It does not replace or refactor them.

## New client endpoint

`guide.json` is an application-facing index joining the active channel guide to the currently published playlist. Channels can remain in the intended guide with `available: false`; clients must degrade gracefully.

## Prototype target

Load guide → browse → play HLS → change channel → show guide → expose Cast on sender-capable Android devices.

The Android project lives at `/android`.
