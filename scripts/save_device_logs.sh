#!/usr/bin/env bash
# Saves the emulator's logs, all of its buffers, to <dir>/logcat.txt. Run by
# .github/workflows/ci.yml after each run of the device tests, pass or fail.
#
# Now and then adb loses the emulator in the middle of a run ("adb: device offline") while the
# emulator itself keeps running. The run then fails without saying which test was running, and
# without logs: logcat can't be read from a device adb has lost. So it reconnects first, to save
# the logs that say what happened.
#
# Usage: save_device_logs.sh <dir>
set -uo pipefail

dir=$1
mkdir -p "$dir"
if [ "$(adb get-state 2> /dev/null)" != device ]; then
  echo "::warning::adb lost the emulator; reconnecting to save its logs"
  adb reconnect offline
  timeout 120 adb wait-for-device || echo "::warning::adb couldn't reconnect to the emulator"
fi
adb logcat -d -b all > "$dir/logcat.txt"
