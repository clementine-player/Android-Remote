# Headless Clementine with the network remote enabled and a small, deterministic
# music library, for the Android remote's integration tests.
#
# Installs a Clementine release .deb for Ubuntu Resolute, which Clementine's CI
# publishes with every release. Put it next to this file first, e.g.:
#   gh release download --repo clementine-player/Clementine \
#     --pattern '*resolute_amd64.deb' --dir clementine-it
#   docker build -t clementine-it clementine-it
#   docker run --rm -p 5500:5500 clementine-it
#
# --build-arg LIBRARY=showcase builds it with the library the store screenshots
# show (showcase-library.tsv) instead of the test library.

ARG UBUNTU=ubuntu:resolute

# The test library is ten short tagged tracks: two artists, two albums each; see
# generate-music.sh.
FROM ${UBUNTU} AS music
ARG LIBRARY=test
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY generate-music.sh showcase-library.tsv /usr/local/bin/
RUN if [ "$LIBRARY" = showcase ]; then generate-music.sh --showcase /music; \
    else generate-music.sh /music; fi

FROM ${UBUNTU}
ENV DEBIAN_FRONTEND=noninteractive
COPY clementine_*resolute_amd64.deb /tmp/
# apt resolves the .deb's Depends. PulseAudio's null sink gives real-time
# playback without audio hardware.
RUN apt-get update && apt-get install -y --no-install-recommends \
      /tmp/clementine_*.deb \
      dbus-daemon pulseaudio gstreamer1.0-pulseaudio sqlite3 netcat-openbsd \
    && rm -rf /var/lib/apt/lists/* /tmp/clementine_*.deb
COPY --from=music /music /music
COPY entrypoint.sh /usr/local/bin/

RUN useradd --create-home clementine
USER clementine
WORKDIR /home/clementine
# No display: Qt renders offscreen, as in Clementine's own CI smoke tests.
ENV QT_QPA_PLATFORM=offscreen

# Seed Clementine's database with /music as a library directory. This runs
# Clementine once so it creates its own schema, then adds the directory row;
# the library is scanned on the next start.
RUN entrypoint.sh --seed

ENV AUTH_CODE=""
EXPOSE 5500
HEALTHCHECK --interval=2s --timeout=2s --start-period=120s CMD test -f /tmp/clementine-ready
ENTRYPOINT ["entrypoint.sh"]
