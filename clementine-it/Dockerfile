# Headless Clementine with the network remote enabled and a small, deterministic
# music library, for the Android remote's integration tests.
#
# Build (from the repository root, with a Clementine checkout next to it):
#   docker build -t clementine-it \
#     --build-context clementine=../Clementine clementine-it
# Run:
#   docker run --rm -p 5500:5500 clementine-it
#
# The dependency list mirrors the Ubuntu Noble job in Clementine's own CI
# (.github/workflows/all.yml), minus the Debian packaging tools.

ARG UBUNTU=ubuntu:noble

FROM ${UBUNTU} AS build
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      cmake g++ gettext git make \
      libasound2-dev libboost-dev libboost-serialization-dev libcdio-cdda2 \
      libcdio-dev libchromaprint-dev libcrypto++-dev libdbus-1-dev \
      libfftw3-dev libgl1-mesa-dev libglew-dev libglx-dev libgpod-dev \
      libgstreamer-plugins-base1.0-dev libgstreamer1.0-dev libmtp-dev \
      libplist-dev libprotobuf-dev libpulse-dev libqt6core5compat6-dev \
      libqt6opengl6-dev libsparsehash-dev libsqlite3-dev libtag1-dev \
      libusbmuxd-dev libx11-dev libxcb1-dev protobuf-compiler \
      qt6-base-dev qt6-base-private-dev qt6-l10n-tools qt6-tools-dev \
      qt6-tools-dev-tools \
    && rm -rf /var/lib/apt/lists/*
COPY --from=clementine . /src
RUN cmake -S /src -B /build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/usr \
      -DENABLE_SPOTIFY_BLOB=OFF \
    && cmake --build /build -j"$(nproc)" \
    && DESTDIR=/install cmake --install /build \
    && ldd /install/usr/bin/clementine | awk '/=> \// {print $3}' \
       | xargs -r dpkg -S 2>/dev/null | cut -d: -f1 | sort -u > /runtime-packages.txt

# Ten short tagged tracks: two artists, two albums each; see generate-music.sh.
FROM ${UBUNTU} AS music
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY generate-music.sh /usr/local/bin/
RUN generate-music.sh /music

FROM ${UBUNTU}
ENV DEBIAN_FRONTEND=noninteractive
# The build stage recorded which packages provide the libraries Clementine
# links against; install those plus what it needs at runtime.
COPY --from=build /install/ /
COPY --from=build /runtime-packages.txt /tmp/
RUN apt-get update && apt-get install -y --no-install-recommends \
      xvfb xauth dbus dbus-x11 pulseaudio sqlite3 netcat-openbsd \
      gstreamer1.0-plugins-base gstreamer1.0-plugins-good gstreamer1.0-pulseaudio \
      qt6-qpa-plugins libqt6sql6-sqlite \
      $(cat /tmp/runtime-packages.txt) \
    && ! ldd /usr/bin/clementine | grep "not found" \
    && rm -rf /var/lib/apt/lists/* /tmp/runtime-packages.txt
COPY --from=music /music /music
COPY entrypoint.sh /usr/local/bin/

RUN useradd --create-home clementine
USER clementine
WORKDIR /home/clementine

# Seed Clementine's database with /music as a library directory. This runs
# Clementine once so it creates its own schema, then adds the directory row;
# the library is scanned on the next start.
RUN entrypoint.sh --seed

ENV AUTH_CODE=""
EXPOSE 5500
HEALTHCHECK --interval=2s --timeout=2s --start-period=120s CMD test -f /tmp/clementine-ready
ENTRYPOINT ["entrypoint.sh"]
