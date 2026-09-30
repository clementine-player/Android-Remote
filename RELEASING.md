# Releasing

The app is built in two flavors that differ only in their application ID:

| Flavor   | Application ID                 | Distributed through                |
|----------|--------------------------------|------------------------------------|
| `fdroid` | `de.qspool.clementineremote`   | F-Droid, GitHub Releases           |
| `play`   | `org.clementine_player.remote` | Google Play                        |

Google Play keeps `de.qspool.clementineremote` reserved for the original author's account,
so the Play build needs its own ID. F-Droid keeps the original so existing installs upgrade.

## Releases

Releases are automatic, and only come when there is something to tell users
(`.github/workflows/release.yml`).

**Release notes live in commit messages.** A commit that changes something users notice ends
with a `Release-note:` trailer: one line, written for users (what's new, not how it was done):

```
Show the song's lyrics in the player

Release-note: The player shows the song's lyrics, when Clementine finds them.
```

Commits without one (refactoring, tests, CI, docs) never make a release on their own. With
rebase merges, each commit keeps its trailer on `master`.

**Every Monday** the release workflow collects the notes since the last release
(`scripts/plan_release.sh`; run it to see what the next release would be). With none, it does
nothing. With some, it releases:

- It makes a **release commit** on top of `master` that sets the version and writes the notes
  as the fastlane changelog, and pushes it as the tag `v<version>`. Nothing is pushed to
  `master`, which keeps its `-dev` version.
- **F-Droid** builds that tag, reading the version and changelog from it.
- A **GitHub release** gets every note, and the APK, signed with the release key in Cloud KMS.
- **Google Play** gets the bundle on the closed and open testing tracks (`alpha,beta`, or the
  repository variable `PLAY_RELEASE_TRACK`, which takes one track or several separated by
  commas), with the notes as its release notes: at most 500 characters, so a long list ends
  with "And more fixes and improvements." Closed testing also gets every development build,
  so there a release is soon followed by newer builds; open testing gets only releases.

**Versions.** Releases are named after `master`'s `versionName` without `-dev`: 13, then
13.1, 13.2 and so on. For a major version, change `master` to `14-dev`. Version codes come
from `master`'s commit count: twice it for the internal testing builds (`dev.yml`), and one
more for a release, so every build's code is higher than the one before on both Play and
F-Droid.

To release before Monday, run the workflow by hand (*Actions → release → Run workflow*). Run
by hand, it releases any change since the last release: when no commit has a note, the release
notes just say "Fixes and improvements." If a release failed after its tag was pushed (Google
Play refused the upload, say), run it with that tag to publish it again.

### One-time setup for releases

1. **Google Cloud.** Review [scripts/gcp_release_setup.sh](scripts/gcp_release_setup.sh) and
   run it, after `gcp_play_setup.sh`, as an admin of `clementine-data`. It creates the release
   key (a second key, not Play's upload key) and lets the service account sign with it.
2. **Make the release certificate** and commit it as `app/release_cert.pem`, as for the
   upload certificate below. Every GitHub release must be signed with it, so this is done
   once:

   ```sh
   $signer gencert \
     --key projects/clementine-data/locations/global/keyRings/android-signing/cryptoKeys/github-release/cryptoKeyVersions/1 \
     --subject "CN=Clementine Remote,O=Clementine" --out app/release_cert.pem
   ```

3. **Google Play:** create the closed testing track's testers list in Play Console (*Testing →
   Closed testing*), and set up open testing (*Testing → Open testing*: the countries it's
   available in). The service account's *Release apps to testing tracks* permission covers
   both; releasing to production needs *Release to production* too, and `PLAY_RELEASE_TRACK`
   set to include `production`.
4. If `v*` tags get a repository ruleset, let GitHub Actions bypass it: the workflow pushes
   the tags.

Until each certificate is committed, the release skips its part with a notice: without
`release_cert.pem` the GitHub release has no APK, and without `upload_cert.pem` nothing goes
to Google Play. With neither, nothing is released at all, not even the tag.

## Development builds

Every push to `master` that changes the app (not only its tests, docs, CI or store listing)
makes a development build (`.github/workflows/dev.yml`):

- on Google Play's **internal testing** track: a private channel for up to 100 testers, with
  no review;
- on its **closed testing** track, for the testers on its list. Unlike internal testing, each
  build there goes through Google's review first, which is usually quick for testing tracks;
- as a GitHub pre-release, `dev-<version code>`, which replaces the last one: the APK, with
  the same package and signature as the releases, so it updates to the next release and from
  the last one. It needs the release certificate (see *One-time setup for releases*).
  Releases are immutable, so each build is a pre-release of its own: link to the
  [releases](https://github.com/clementine-player/Android-Remote/releases) rather than to one.

The version code is twice the number of commits on `master` (releases take the odd codes),
and the version name is `versionName` plus the commit, such as `13-dev+6ece743`.

The rest of this section sets up Google Play.

There are no keys or credentials in the repository or its secrets:

- The **upload key** is a Cloud KMS key in the `clementine-data` project that cannot be
  exported. The bundle is built unsigned and signed by
  [kms-signer](https://github.com/clementine-player/kms-signer), which sends only digests to KMS.
  `dev.yml` pins a kms-signer release by version and checksum. Play
  re-signs the app with its own app signing key (Play App Signing).
- GitHub Actions authenticates to Google Cloud with **Workload Identity Federation**, through
  the `github-actions` pool Clementine's macOS signing already uses. This repository has its
  own provider in that pool, which admits only its `master` branch, and its own service
  account, `android-play-release`, which can sign with the upload key and nothing else. The
  same identity uploads to Play.

Until `app/upload_cert.pem` is committed, the workflow only builds the unsigned bundle.

### One-time setup

1. **Google Cloud.** Review [scripts/gcp_play_setup.sh](scripts/gcp_play_setup.sh), fill in
   `MAINTAINER_EMAILS`, and run it as an admin of `clementine-data`. It creates the upload key
   (RSA 3072, in an HSM), the service account, and the provider, and lets the listed
   maintainers impersonate the service account for local signing. The identifiers it creates
   are the ones `.github/workflows/dev.yml` already uses.

2. **Make the signing certificate** and commit it as `app/upload_cert.pem`. Every release must
   be signed with the same certificate, so this is done once, as the service account:

   ```sh
   gcloud auth application-default login \
     --impersonate-service-account=android-play-release@clementine-data.iam.gserviceaccount.com
   # kms-signer from https://github.com/clementine-player/kms-signer/releases (see its README)
   $signer gencert \
     --key projects/clementine-data/locations/global/keyRings/android-signing/cryptoKeys/play-upload/cryptoKeyVersions/1 \
     --subject "CN=Clementine Remote Upload,O=Clementine" --out app/upload_cert.pem
   ```

3. **Create the app** in [Play Console](https://play.google.com/console): *Create app*, name
   *Clementine Remote*, app, free. Under *Testing → Internal testing*, create an email list of
   testers and add it to the track. Play only accepts API uploads for an app after its first
   bundle was uploaded in the console, so sign one locally, with the same login as step 2,
   and upload it by hand:

   ```sh
   ./gradlew bundlePlayRelease -PversionCodeOverride=$(( 2 * $(git rev-list --count HEAD) ))
   $signer sign \
     --key projects/clementine-data/locations/global/keyRings/android-signing/cryptoKeys/play-upload/cryptoKeyVersions/1 \
     --cert app/upload_cert.pem --min-sdk 23 \
     --in app/build/outputs/bundle/playRelease/ClementineRemote-play-release.aab \
     --out ClementineRemote-play-release-signed.aab
   ```

   This upload also enrols the app in Play App Signing with a Google-generated app signing
   key, and registers `upload_cert.pem` as its upload certificate. Roll the release out to
   internal testing and share the opt-in link with testers.

4. **Let the service account upload:** in Play Console, *Users and permissions → Invite new
   users*, invite `android-play-release@clementine-data.iam.gserviceaccount.com` with
   *Release apps to testing tracks* for this app.

5. Run the *dev* workflow (*Actions → dev → Run workflow*, on `master`) to check it.

If Play rejects uploads with "Only releases with status draft may be created on draft app",
the first release has not been rolled out yet (step 3): either do that, or set the repository
variable `PLAY_RELEASE_STATUS` to `draft` and roll each release out by hand.

### Before testing more widely

Closed or open testing and production go through review, which needs the *App content*
section of Play Console completed:

- **Privacy policy:** link to [PRIVACY.md](PRIVACY.md).
- **Data safety:** no data collected or shared.
- **Foreground service:** the service uses the `connectedDevice` type to stay connected to
  Clementine while music plays; Play asks for a description and a short video of it.
- **Phone state:** `READ_PHONE_STATE` is used to lower the volume during calls.

New personal developer accounts must also run a closed test with at least 12 testers for
14 days before production access is granted.
