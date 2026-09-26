#!/usr/bin/env bash
# Checks that a release tag matches the app's version, and that its changelog is ready. Run by
# .github/workflows/release.yml, and before tagging:
#
#   scripts/check_release.sh v13
#
# Prints the version code and name as key=value lines (for $GITHUB_OUTPUT), and the path of
# the changelog that F-Droid, Google Play and the GitHub release all show.
set -euo pipefail

tag=${1:?usage: check_release.sh <tag, such as v13>}
cd "$(dirname "$0")/.."

gradle=app/build.gradle.kts
code=$(sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p' "$gradle")
name=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' "$gradle")
changelog="fastlane/metadata/android/en-US/changelogs/$code.txt"
fail() {
  echo "::error::$1" >&2
  exit 1
}

[ -n "$code" ] && [ -n "$name" ] || fail "Couldn't read versionCode and versionName from $gradle."
[ "$tag" = "v$name" ] || fail "The tag $tag doesn't match versionName \"$name\" in $gradle: set versionName to \"${tag#v}\" (and bump versionCode) for this release."
case $name in
  *-dev* | *-SNAPSHOT*) fail "versionName \"$name\" is a development version: set the release's version first." ;;
esac
[ -f "$changelog" ] || fail "There is no changelog for version code $code: write $changelog."
[ -s "$changelog" ] || fail "$changelog is empty."
# Google Play's limit for release notes; F-Droid shows the same text.
length=$(wc -m < "$changelog")
[ "$length" -le 500 ] || fail "$changelog is $length characters; Google Play takes at most 500."

echo "code=$code"
echo "name=$name"
echo "changelog=$changelog"
