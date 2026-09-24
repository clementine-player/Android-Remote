# Integration tests against a real Clementine

This directory builds a Docker image that runs Clementine headless, with these settings:

- Xvfb provides the display.
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
git clone https://github.com/clementine-player/Clementine ../Clementine
docker build -t clementine-it --build-context clementine=../Clementine clementine-it
docker run --rm -d --name clementine -p 5500:5500 -e AUTH_CODE=12345 clementine-it
# wait until `docker inspect -f '{{.State.Health.Status}}' clementine` says healthy
./gradlew testDebugUnitTest --tests 'de.qspool.clementineremote.integration.*' \
    -Pclementine.host=localhost -Pclementine.authCode=12345
```

The first image build compiles Clementine, which takes a while. Later builds reuse the Docker layer cache until the Clementine source changes.

## Protocol drift

`scripts/check-proto-drift.py` compares `app/src/main/proto/remotecontrolmessages.proto` with Clementine's `ext/libclementine-remote/remotecontrolmessages.proto`, structure against structure:

- It fails if Clementine changes or removes anything the app uses.
- It lists additions the app doesn't use yet.
