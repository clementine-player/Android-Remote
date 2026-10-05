#!/bin/sh
# Generates a music library of tagged Ogg Vorbis tracks of sine tones.
#
#   generate-music.sh [dir]             the test library: 10 tracks of 30s with
#                                       predictable tags. Tests rely on these
#                                       exact values.
#   generate-music.sh --showcase [dir]  the library in showcase-library.tsv, for
#                                       the store screenshots, with the album
#                                       covers in covers/.
#   generate-music.sh --demo [dir]      real recordings of the showcase works, for
#                                       the demo Clementine: demo-library.tsv's,
#                                       downloaded from Wikimedia Commons (needs
#                                       curl), with the same covers.
set -eu

# cover <album> <dir>: the album's cover, where Clementine looks for one: an image in its folder.
cover() {
  cover="$(dirname "$0")/covers/$1.jpg"
  if [ -f "$cover" ]; then cp "$cover" "$2/cover.jpg"; fi
}

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
    cover "$album" "$out/$artist/$album"
  done
  ls -R "$out"
  exit 0
fi

if [ "${1:-}" = "--demo" ]; then
  out=${2:-/music}
  download=$(mktemp -d)
  grep -v '^#' "$(dirname "$0")/demo-library.tsv" |
  while IFS="$(printf '\t')" read -r artist album year track title performer licence sha1 file; do
    url="https://commons.wikimedia.org/wiki/Special:FilePath/$(printf %s "$file" | sed 's/ /_/g')"
    # Wikimedia asks for a User-Agent that says who's asking.
    curl -fsSL --retry 3 -A "clementine-it/1.0 (https://github.com/clementine-player/Android-Remote)" \
      -o "$download/track" "$url"
    if ! echo "$sha1  $download/track" | sha1sum -c --status; then
      echo "$file isn't the recording demo-library.tsv names (SHA-1 $sha1)" >&2
      exit 1
    fi
    dir="$out/$artist/$album"
    mkdir -p "$dir"
    ffmpeg -nostdin -loglevel error -i "$download/track" -map 0:a -map_metadata -1 \
      -c:a libvorbis -q:a 5 \
      -metadata artist="$artist" -metadata albumartist="$artist" -metadata composer="$artist" \
      -metadata performer="$performer" -metadata album="$album" -metadata title="$title" \
      -metadata track="$track" -metadata date="$year" -metadata genre=Classical \
      -metadata comment="$performer. $licence, from Wikimedia Commons: $file" \
      "$dir/$(printf %02d "$track") $title.ogg"
    cover "$album" "$dir"
  done
  rm -rf "$download"
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
