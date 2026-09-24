#!/bin/sh
# Generates the test library: 10 Ogg Vorbis tracks of 30s sine tones with
# predictable tags. Tests rely on these exact values.
set -eu
out=${1:-/music}
n=0
for artist in "Test Artist A" "Test Artist B"; do
  for album in "Album One" "Album Two"; do
    tracks=3
    [ "$album" = "Album Two" ] && tracks=2
    t=1
    while [ "$t" -le "$tracks" ]; do
      n=$((n + 1))
      dir="$out/$artist/$album"
      mkdir -p "$dir"
      ffmpeg -loglevel error -f lavfi -i "sine=frequency=$((220 + n * 55)):duration=30" \
        -c:a libvorbis -q:a 0 \
        -metadata artist="$artist" -metadata albumartist="$artist" \
        -metadata album="$album" -metadata title="Track $(printf %02d "$n")" \
        -metadata track="$t" -metadata date=2020 -metadata genre=Test \
        "$dir/$(printf %02d "$t") Track $(printf %02d "$n").ogg"
      t=$((t + 1))
    done
  done
done
ls -R "$out"
