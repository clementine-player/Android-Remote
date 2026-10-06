#!/usr/bin/env bash
# Decides whether there is a release to make, and what it is. Run by
# .github/workflows/release.yml on master, and runnable locally to see what the next release
# would be:
#
#   scripts/plan_release.sh [notes directory]
#
# A release is due when commits since the last release (the newest v* tag) carry release
# notes: a "Release-note:" trailer in the commit message, one line for users. Commits without
# one (refactoring, tests, CI) don't make a release, unless ALWAYS is set (a release run by hand):
# then any commit since the last release makes one, with a general note if none has its own.
#
# Prints key=value lines (for $GITHUB_OUTPUT):
#   release  true, or false when there's nothing to release
#   name     the version name: master's versionName without "-dev" (13), then 13.1, 13.2, ...
#   code     the version code: twice master's commit count, plus one. Google Play's internal
#            builds (dev.yml) take twice the count, so the two never collide and both rise.
#   last     the previous release's tag
# and writes, into the notes directory (default: a new temporary one, printed as notes=):
#   changelog.txt  the notes for Google Play and F-Droid, cut to Play's 500 characters
#   notes.md       every note, for the GitHub release
set -euo pipefail

cd "$(dirname "$0")/.."
master=${MASTER:-origin/master}
notes_dir=${1:-$(mktemp -d)}
mkdir -p "$notes_dir"

last=$(git tag --list 'v[0-9]*' --sort=-v:refname | head -n 1)
echo "last=$last"

# The notes of every commit since then, oldest first, each once: the nightly translations
# commits (translations.yml) all have the same one.
notes=$(git log --reverse --format='%(trailers:key=Release-note,valueonly,separator=%x0A)' \
  ${last:+"$last.."}"$master" | sed '/^[[:space:]]*$/d' | awk '!seen[$0]++')
if [ -z "$notes" ] && [ -n "${ALWAYS:-}" ] && [ -n "$(git rev-list ${last:+"$last.."}"$master")" ]; then
  notes="Fixes and improvements."
fi
if [ -z "$notes" ]; then
  echo "release=false"
  exit 0
fi

# 13-dev -> 13; then the next unused of 13, 13.1, 13.2, ...
base=$(git show "$master:app/build.gradle.kts" | sed -n 's/^ *versionName = "\([^"-]*\).*"$/\1/p')
[ -n "$base" ] || { echo "::error::Couldn't read versionName from app/build.gradle.kts." >&2; exit 1; }
name=$base
minor=0
while git rev-parse -q --verify "refs/tags/v$name" > /dev/null; do
  minor=$((minor + 1))
  name="$base.$minor"
done
code=$(( 2 * $(git rev-list --count "$master") + 1 ))

printf '%s\n' "$notes" | sed 's/^/- /' > "$notes_dir/notes.md"
# Google Play takes at most 500 characters: whole notes, as many as fit, then a note that
# there's more.
more="- And more fixes and improvements."
: > "$notes_dir/changelog.txt"
while IFS= read -r note; do
  line="- $note"
  if [ $(( $(wc -m < "$notes_dir/changelog.txt") + ${#line} + ${#more} + 2 )) -gt 500 ]; then
    echo "$more" >> "$notes_dir/changelog.txt"
    break
  fi
  echo "$line" >> "$notes_dir/changelog.txt"
done <<< "$notes"

echo "release=true"
echo "name=$name"
echo "code=$code"
echo "notes=$notes_dir"
