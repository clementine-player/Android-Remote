#!/bin/sh
# Generates a music library of tagged Ogg Vorbis tracks of sine tones.
#
#   generate-music.sh [dir]             the test library: 10 tracks of 30s with
#                                       predictable tags. Tests rely on these
#                                       exact values.
#   generate-music.sh --showcase [dir]  the library in showcase-library.tsv, for
#                                       the store screenshots.
set -eu

tone() { # tone <n> <seconds> <file> <ffmpeg metadata args...>
  n=$1 seconds=$2 file=$3
  shift 3
  mkdir -p "$(dirname "$file")"
  # -nostdin: the showcase loop feeds its list on stdin, which ffmpeg would read.
  ffmpeg -nostdin -loglevel error -f lavfi -i "sine=frequency=$((220 + n * 55)):duration=$seconds" \
    -c:a libvorbis -q:a 0 "$@" "$file"
}

if [ "${1:-}" = "--showcase" ]; then
  out=${2:-/music}
  n=0
  t=0
  last_album=
  grep -v '^#' "$(dirname "$0")/showcase-library.tsv" |
  while IFS="$(printf '\t')" read -r artist album year title seconds; do
    n=$((n + 1))
    [ "$album" = "$last_album" ] || t=0
    last_album=$album
    t=$((t + 1))
    tone "$n" "$seconds" "$out/$artist/$album/$(printf %02d "$t") $title.ogg" \
      -metadata artist="$artist" -metadata albumartist="$artist" -metadata composer="$artist" \
      -metadata album="$album" -metadata title="$title" -metadata track="$t" \
      -metadata date="$year" -metadata genre=Classical
  done
  ls -R "$out"
  exit 0
fi

out=${1:-/music}
n=0
for artist in "Test Artist A" "Test Artist B"; do
  for album in "Album One" "Album Two"; do
    tracks=3
    [ "$album" = "Album Two" ] && tracks=2
    t=1
    while [ "$t" -le "$tracks" ]; do
      n=$((n + 1))
      tone "$n" 30 "$out/$artist/$album/$(printf %02d "$t") Track $(printf %02d "$n").ogg" \
        -metadata artist="$artist" -metadata albumartist="$artist" \
        -metadata album="$album" -metadata title="Track $(printf %02d "$n")" \
        -metadata track="$t" -metadata date=2020 -metadata genre=Test
      t=$((t + 1))
    done
  done
done
ls -R "$out"
