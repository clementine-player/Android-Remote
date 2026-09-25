#!/bin/bash
# Starts Clementine headless (Qt offscreen platform, PulseAudio null sink so
# playback runs in real time) with the network remote on port 5500 and /music loaded into the
# active playlist.
#
#   entrypoint.sh           run Clementine in the foreground
#   entrypoint.sh --seed    image build step: create the database, add /music
#
# Environment: AUTH_CODE (enables the remote's auth code when set).
set -euo pipefail

CONFIG_DIR="$HOME/.config/Clementine"
DB="$CONFIG_DIR/clementine.db"

write_config() {
  mkdir -p "$CONFIG_DIR"
  local use_auth=false
  [ -n "${AUTH_CODE:-}" ] && use_auth=true
  cat > "$CONFIG_DIR/Clementine.conf" <<CONF
[NetworkRemote]
use_remote=true
port=5500
only_non_public_ip=false
use_auth_code=$use_auth
auth_code=${AUTH_CODE:-0}
allow_downloads=true
convert_lossless=false

[GstEngine]
sink=pulsesink

[MainWindow]
# Remote CHANGE_SONG plays the song rather than queueing it.
doubleclick_playlist_addmode=1

[General]
startupbehaviour=1
CONF
}

start_audio() {
  pulseaudio --start --exit-idle-time=-1 --daemonize=yes \
    --load=module-null-sink --log-target=stderr 2>/tmp/pulse.log || true
}

wait_for() {
  local what=$1 check=$2
  for _ in $(seq 300); do
    if eval "$check"; then return 0; fi
    sleep 0.2
  done
  echo "Timed out waiting for $what" >&2
  return 1
}

write_config
start_audio

if [ "${1:-}" = "--seed" ]; then
  dbus-run-session -- clementine --quiet >/tmp/clementine-seed.log 2>&1 &
  pid=$!
  wait_for "database schema" \
    "[ -f '$DB' ] && sqlite3 '$DB' 'select 1 from directories limit 1' >/dev/null 2>&1"
  sleep 2
  kill "$pid"; wait "$pid" || true
  sqlite3 "$DB" "insert into directories (path, subdirs) values ('/music', 1);"
  echo "Seeded $DB with /music"
  exit 0
fi

dbus-run-session -- clementine --verbose &
pid=$!
trap 'kill $pid 2>/dev/null' TERM INT

wait_for "network remote" "nc -z localhost 5500"
tracks=$(find /music -name '*.ogg' | wc -l)
wait_for "library scan" \
  "[ \"\$(sqlite3 '$DB' 'select count(*) from songs where unavailable = 0' 2>/dev/null)\" = $tracks ]"
# Replace the playlist with the library, in path order, and leave it stopped
# (loading starts playback).
find /music -name '*.ogg' | sort | xargs -d '\n' clementine --load >/dev/null 2>&1
sleep 1
clementine --stop >/dev/null 2>&1
touch /tmp/clementine-ready
echo "Clementine ready on port 5500"

set +e
wait "$pid"
status=$?
echo "Clementine exited with status $status" >&2
exit "$status"
