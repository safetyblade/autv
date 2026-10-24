#!/usr/bin/env bash
# Synthetic AUTV-owned fixture; no broadcaster content or external downloads.
set -euo pipefail
cd "$(dirname "$0")/.."
fixture_dir=android/app/src/debug/assets/recovery
mkdir -p "$fixture_dir"
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc2=size=480x270:rate=25 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 -t 24 \
  -vf "drawtext=text='AUTV LOCAL RECOVERY':fontcolor=white:fontsize=25:box=1:boxcolor=black@0.7:x=(w-tw)/2:y=h-45" \
  -c:v libx264 -profile:v baseline -level 3.0 -pix_fmt yuv420p -preset veryfast \
  -b:v 160k -maxrate 200k -bufsize 400k -g 200 -keyint_min 200 -sc_threshold 0 \
  -c:a aac -b:a 48k -ac 2 -f hls -hls_time 8 -hls_playlist_type vod \
  -hls_segment_filename "$fixture_dir/recovery-%d.ts" "$fixture_dir/playlist.m3u8" -y
