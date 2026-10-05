#!/usr/bin/env bash
# Installs a minified release build on the connected device, starts it, and fails if it crashes.
# Every other test runs a debug build, which R8 doesn't touch, so this is the only check that the
# R8 rules keep what the app and its libraries reach by reflection: a release build once removed
# the constructor WorkManager's database is made with, and crashed at launch on every phone. Run
# by .github/workflows/ci.yml on its emulators.
#
# Usage: smoke_test_release.sh <unsigned release APK>
#
# Needs: adb, a single connected device, keytool, and ANDROID_HOME with build-tools.
set -euo pipefail

unsigned=$1
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# CI has no release key, so the APK is signed with a throwaway one.
build_tools=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 | sort -V | tail -1)
keytool -genkeypair -keystore "$work/key.jks" -storepass smoketest -alias smoke -keyalg RSA \
  -keysize 2048 -validity 1 -dname CN=smoke-test > /dev/null 2>&1
"$build_tools/apksigner" sign --ks "$work/key.jks" --ks-pass pass:smoketest \
  --out "$work/app.apk" "$unsigned"
package=$("$build_tools/aapt2" dump packagename "$work/app.apk")

# Each run signs with a new key, which Android won't install over an earlier run's.
adb uninstall "$package" > /dev/null 2>&1 || true
adb install "$work/app.apk"
adb logcat -c -b crash
adb shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
# A crash in startup comes within a second or two; the wait also covers a slow emulator.
sleep 15

crash=$(adb logcat -d -b crash | grep -A40 "Process: $package," || true)
if [ -n "$crash" ] || ! adb shell pidof "$package" > /dev/null; then
  echo "::error::The release build of $package crashed or stopped after starting"
  printf '%s\n' "$crash"
  exit 1
fi
echo "The release build of $package started and is still running"
adb uninstall "$package" > /dev/null
