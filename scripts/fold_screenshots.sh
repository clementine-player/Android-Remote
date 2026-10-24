#!/usr/bin/env bash
# Takes the player on a foldable emulator, for pull requests: on its outer screen, on its inner
# screen open flat, and on its inner screen half-open like a book, where the player sits on
# either side of the fold. Light and dark each (StoreScreenshots.takeFoldScreenshots). Run by
# .github/workflows/store-screenshots.yml on a Pixel Fold emulator, with Clementine running.
#
# The posture is set from here, through the emulator's console, between runs of the test: fold
# and unfold switch between the screens, and a hinge angle of 90° half-opens it (the Pixel Fold's
# fold is vertical, so half-open is like a book). The test's screenshots are of the screen in use.
#
# Usage: fold_screenshots.sh <Clementine's host> <screenshots dir>
set -uo pipefail

host=$1
out=$2
shots=/sdcard/Android/data/de.qspool.clementineremote/files/screenshots
mkdir -p "$out"

./gradlew installFdroidDebug installFdroidDebugAndroidTest || exit 1

status=0
take() {
  local posture=$1
  # Folding turns the screen off and moves to the other one: wake it, and let it settle. The
  # test starts the app afresh, so the app relaunching for the new posture doesn't matter.
  sleep 5
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard
  sleep 2
  adb shell wm size
  adb shell cmd device_state print-state
  local result
  result=$(adb shell am instrument -w \
    -e class de.qspool.clementineremote.StoreScreenshots#takeFoldScreenshots \
    -e clementineHost "$host" -e posture "$posture" \
    de.qspool.clementineremote.test/androidx.test.runner.AndroidJUnitRunner)
  echo "$result"
  # am instrument exits 0 whatever the tests did.
  grep -q '^OK (1 test)' <<< "$result" || status=1
  local dir
  dir=$(mktemp -d)
  adb pull "$shots/." "$dir" > /dev/null
  cp "$dir"/fold_*.png "$dir"/dark_fold_*.png "$out"/ 2> /dev/null
  # What a failure left, by the name post_screenshots.sh shows failures under.
  [ -f "$dir/failure.png" ] && cp "$dir/failure.png" "$out/failure-fold-$posture.png"
  rm -rf "$dir"
}

adb emu unfold
take inner
adb emu fold
take outer
adb emu unfold
adb emu sensor set hinge-angle0 90
take half_open

exit $status
