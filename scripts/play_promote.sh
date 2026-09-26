#!/usr/bin/env bash
# Puts a build that Google Play already has (uploaded to internal testing by play.yml) on
# another track, with release notes. Run by .github/workflows/release.yml:
#
#   PLAY_TOKEN=... scripts/play_promote.sh <package> <version code> <release name> <track> <status> <notes file>
#
# PLAY_TOKEN is an OAuth access token for the androidpublisher scope; in CI, the service
# account's, from Workload Identity Federation. The build is looked for for up to 20 minutes,
# as play.yml may still be uploading it when the tag is pushed.
set -euo pipefail

package=$1 code=$2 name=$3 track=$4 status=$5 notes=$6
# PLAY_API and PLAY_POLL_SECONDS are for testing against a stand-in for the API.
api="${PLAY_API:-https://androidpublisher.googleapis.com/androidpublisher/v3}/applications/$package"
auth=(-H "Authorization: Bearer ${PLAY_TOKEN:?}")

call() {
  curl -sS --fail-with-body "${auth[@]}" -H "Content-Type: application/json" "$@"
}

for attempt in $(seq 1 20); do
  edit=$(call -X POST "$api/edits" -d '{}' | jq -r .id)
  if call "$api/edits/$edit/bundles" | jq -e --argjson code "$code" \
      '[.bundles[]?.versionCode] | index($code) != null' > /dev/null; then
    break
  fi
  call -X DELETE "$api/edits/$edit" > /dev/null || true
  if [ "$attempt" = 20 ]; then
    echo "::error::Google Play has no build with version code $code: did play.yml upload this commit?" >&2
    exit 1
  fi
  echo "Version code $code isn't on Google Play yet; waiting for play.yml ($attempt/20)"
  sleep "${PLAY_POLL_SECONDS:-60}"
done

body=$(jq -n --arg track "$track" --arg name "$name" --arg code "$code" --arg status "$status" \
  --rawfile text "$notes" \
  '{track: $track, releases: [{name: $name, versionCodes: [$code], status: $status,
    releaseNotes: [{language: "en-US", text: $text}]}]}')
call -X PUT "$api/edits/$edit/tracks/$track" -d "$body" > /dev/null
call -X POST "$api/edits/$edit:commit" > /dev/null
echo "Released $name ($code) on the $track track ($status)"
