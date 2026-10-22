#!/usr/bin/env bash
# Saves the emulator's logs, all of its buffers, to <dir>/logcat.txt. Run by
# .github/workflows/ci.yml after each run of the device tests, pass or fail.
#
# Now and then adb loses the emulator in the middle of a run ("adb: device offline") while the
# emulator itself keeps running. The run then fails without saying which test was running, and
# without logs: logcat can't be read from a device adb has lost. So it reconnects first, to save
# the logs that say what happened.
#
# Given the run's exit status, and it isn't 0, it also prints what the logs say about the
# emulator's state into the job's log, in a group of its own: how long it has been up (a
# restart of Android shows there), its memory, and the lines from adbd, the low memory killer,
# the test runner and crashes. The artifact has the whole logcat, but can't be downloaded from
# everywhere the job's log can be read.
#
# Usage: save_device_logs.sh <dir> [exit status of the run]
set -uo pipefail

dir=$1
status=${2:-0}
mkdir -p "$dir"
if [ "$(adb get-state 2> /dev/null)" != device ]; then
  echo "::warning::adb lost the emulator; reconnecting to save its logs"
  adb reconnect offline
  timeout 120 adb wait-for-device || echo "::warning::adb couldn't reconnect to the emulator"
fi
adb logcat -d -b all > "$dir/logcat.txt"

if [ "$status" -ne 0 ]; then
  echo "::group::The emulator's state after the failed run"
  adb shell 'uptime; grep -E "^(MemTotal|MemFree|MemAvailable|SwapTotal|SwapFree):" /proc/meminfo'
  grep -E -i 'adbd|lowmemorykiller|lmkd|out of memory|oom-kill|watchdog|FATAL EXCEPTION|AndroidRuntime|TestRunner|am_crash|am_anr|Zygote *:.*(died|crash)|system_server' \
    "$dir/logcat.txt" | tail -n 300
  echo "::endgroup::"
fi
