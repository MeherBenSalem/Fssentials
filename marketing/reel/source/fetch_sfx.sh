#!/bin/bash
# Downloads the reel's sound effects from Mixkit (Mixkit Sound Effects Free License: free for commercial use in
# videos, no attribution required) into $1 (default ./sfxlib), as WAV. sfx_pro.py reads them from there.
# A few files are only served as MP3 previews; those are converted to WAV.
set -e
D=${1:-sfxlib}; mkdir -p "$D"
for id in 1143 2908 2901 2950 1490 1481 2568 3124 2350 2583 2354 2357 2867 1993 2155 2143 1110 933 2364; do
  f="$D/$id.wav"; [ -s "$f" ] && continue
  curl -sf --max-time 60 -o "$f" "https://assets.mixkit.co/active_storage/sfx/$id/$id.wav" || rm -f "$f"
  if ! head -c 4 "$f" 2>/dev/null | grep -q RIFF; then
    curl -sf --max-time 60 -o "$D/$id.mp3" "https://assets.mixkit.co/active_storage/sfx/$id/$id-preview.mp3"
    ffmpeg -loglevel error -y -i "$D/$id.mp3" -c:a pcm_s16le "$f" && rm "$D/$id.mp3"
  fi
done
ls "$D" | wc -l
