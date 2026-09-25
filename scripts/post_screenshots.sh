#!/usr/bin/env bash
# Uploads a store-screenshots run's screenshots to the R2 bucket and posts them on the pull
# request, next to the store listing's current screenshots from master. Run by
# .github/workflows/store-screenshots.yml; one comment per pull request, updated in place.
#
# Usage: post_screenshots.sh <screenshots dir> <pull request number>
#
# Needs: R2_ACCOUNT_ID, R2_BUCKET, R2_PUBLIC_URL, AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
# (the bucket-scoped R2 token), GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_ATTEMPT,
# GITHUB_SERVER_URL, GH_TOKEN, and the AWS and GitHub CLIs.
set -euo pipefail
# Name order as scripts/store_graphics.py sorts them (by code point).
export LC_ALL=C

dir=$1
pr=$2
marker='<!-- store-screenshots -->'

shopt -s nullglob
shots=("$dir"/*.png)
if [ ${#shots[@]} -eq 0 ]; then
  echo "No screenshots to post"
  exit 0
fi

# A new folder per run, so GitHub's image cache never shows an earlier run's images. The
# bucket's lifecycle rule deletes them after 30 days.
prefix="pr-$pr/$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
AWS_DEFAULT_REGION=auto \
AWS_REQUEST_CHECKSUM_CALCULATION=when_required \
AWS_RESPONSE_CHECKSUM_VALIDATION=when_required \
  aws s3 cp "$dir" "s3://$R2_BUCKET/$prefix/" --recursive --exclude '*' --include '*.png' \
    --content-type image/png --cache-control 'public, max-age=2592000, immutable' \
    --endpoint-url "https://$R2_ACCOUNT_ID.r2.cloudflarestorage.com" --only-show-errors
base="${R2_PUBLIC_URL%/}/$prefix"

# The store screenshots on master, in the order scripts/store_graphics.py numbers them.
# Compared only when this run took all of them: after a failure the numbers don't line up.
numbered=("$dir"/[0-9]_*.png)
listed=(fastlane/metadata/android/en-US/images/phoneScreenshots/*.png)
compare=$([ ${#numbered[@]} -eq ${#listed[@]} ] && echo yes || echo no)
store="https://raw.githubusercontent.com/$GITHUB_REPOSITORY/master/fastlane/metadata/android/en-US/images/phoneScreenshots"
run="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"

{
  echo "$marker"
  echo "### Store screenshots"
  echo
  echo "From [run $GITHUB_RUN_ID]($run), against clementine-it. Left: the store listing on master. Right: this pull request."
  echo
  echo "| Screen | master | This PR |"
  echo "| --- | --- | --- |"
  i=0
  for shot in "${shots[@]}"; do
    name=$(basename "$shot" .png)
    case $name in
      [0-9]_*)
        i=$((i + 1))
        if [ "$compare" = yes ]; then
          before="<img src=\"$store/$i.png\" width=\"240\">"
        else
          before="–"
        fi
        echo "| \`$name\` | $before | <img src=\"$base/$name.png\" width=\"240\"> |"
        ;;
    esac
  done
  failures=()
  for shot in "${shots[@]}"; do
    case $(basename "$shot") in
      [0-9]_*) ;;
      *) failures+=("$shot") ;;
    esac
  done
  if [ ${#failures[@]} -gt 0 ]; then
    echo
    echo "#### Screens at a failure"
    echo
    for shot in "${failures[@]}"; do
      name=$(basename "$shot" .png)
      echo "\`$name\`<br><img src=\"$base/$name.png\" width=\"240\">"
      echo
    done
  fi
} > "$RUNNER_TEMP/screenshots-comment.md"

existing=$(gh api "repos/$GITHUB_REPOSITORY/issues/$pr/comments" --paginate \
  --jq ".[] | select(.user.login == \"github-actions[bot]\" and (.body | startswith(\"$marker\"))) | .id" \
  | head -n 1)
if [ -n "$existing" ]; then
  gh api -X PATCH "repos/$GITHUB_REPOSITORY/issues/comments/$existing" \
    -F "body=@$RUNNER_TEMP/screenshots-comment.md" > /dev/null
else
  gh api "repos/$GITHUB_REPOSITORY/issues/$pr/comments" \
    -F "body=@$RUNNER_TEMP/screenshots-comment.md" > /dev/null
fi
