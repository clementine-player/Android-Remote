# Integration tests against a real Clementine

This directory builds a Docker image that runs Clementine headless. It installs the Ubuntu Resolute `.deb` from a Clementine GitHub release, and sets it up like this:

- Qt renders offscreen, so no display is needed.
- PulseAudio's null sink provides audio output, so playback runs in real time.
- The network remote is enabled on port 5500.
- The library holds ten generated test tracks (see `generate-music.sh`). They are also loaded as the active playlist.

The tests are in `app/src/test/java/de/qspool/clementineremote/integration/`. They drive the app's own connection, message factory and parser code against that Clementine. They cover:

- auth codes
- the initial state Clementine sends on connect
- playlists
- play, pause, next, previous and seek
- volume, shuffle and repeat
- library and song downloads, checked against Clementine's SHA-1 hashes
- global search

A normal `./gradlew test` skips them.

## Running locally

```sh
gh release download --repo clementine-player/Clementine \
    --pattern '*resolute_amd64.deb' --dir clementine-it
docker build -t clementine-it clementine-it
docker run --rm -d --name clementine -p 5500:5500 -e AUTH_CODE=12345 clementine-it
# wait until `docker inspect -f '{{.State.Health.Status}}' clementine` says healthy
./gradlew testFdroidDebugUnitTest --tests 'de.qspool.clementineremote.integration.*' \
    -Pclementine.host=localhost -Pclementine.authCode=12345
```

CI uses Clementine's latest release by default. You can choose a different release tag when starting the workflow by hand.

## Protocol drift

`scripts/check-proto-drift.py` compares `app/src/main/proto/remotecontrolmessages.proto` with Clementine's `ext/libclementine-remote/remotecontrolmessages.proto`, structure against structure:

- It fails if Clementine changes or removes anything the app uses.
- It lists additions the app doesn't use yet.
