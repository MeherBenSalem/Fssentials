#!/bin/bash
# Mix voice (the channel's narration chain only), music and effects, then burn the overlay onto the base video.
set -e
cd "${REEL_DIR:-$HOME/.cache/donutessentials-smoke/reel}/edit"
D=$(python3 -c "import json;print(json.load(open('cues.json'))['duration'])")
ffmpeg -loglevel error -y -i vo.wav -i music.wav -i sfx.wav -filter_complex "\
[0:a]aresample=48000,highpass=f=75,equalizer=f=3500:t=q:w=1:g=1.5,deesser=i=0.35,acompressor=threshold=-20dB:ratio=2.5:attack=10:release=120,asplit=3[v][vsc][vsc2];\
[1:a]volume=0.40[m];[m][vsc]sidechaincompress=threshold=0.05:ratio=6:attack=15:release=250[md];\
[2:a]volume=0.6[s0];[s0][vsc2]sidechaincompress=threshold=0.08:ratio=2.5:attack=10:release=200[s];\
[v][md][s]amix=inputs=3:normalize=0:duration=longest,alimiter=limit=0.95,loudnorm=I=-13:TP=-1.0:LRA=9,aresample=48000" \
  -ar 48000 -t "$D" mix.wav
ffmpeg -loglevel error -y -i base.mp4 -framerate 30 -i ov/ov_%04d.png -i mix.wav -filter_complex \
  "[0:v][1:v]overlay=0:0:format=auto,format=yuv420p[v]" -map "[v]" -map 2:a \
  -c:v libx264 -preset slow -crf 17 -profile:v high -level 4.2 -r 30 -g 30 \
  -c:a aac -b:a 256k -ar 48000 -movflags +faststart -t "$D" reel.mp4
ffprobe -v error -show_entries format=duration,size -show_entries stream=codec_name,width,height,r_frame_rate -of compact reel.mp4
