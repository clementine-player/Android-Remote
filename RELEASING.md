# Releasing

The app is built in two flavors that differ only in their application ID:

| Flavor   | Application ID                 | Distributed through                |
|----------|--------------------------------|------------------------------------|
| `fdroid` | `de.qspool.clementineremote`   | F-Droid, GitHub Releases           |
| `play`   | `org.clementine_player.remote` | Google Play                        |

Google Play keeps `de.qspool.clementineremote` reserved for the original author's account,
so the Play build needs its own ID. F-Droid keeps the original so existing installs upgrade.

## Releasing a version

A version tag publishes the release everywhere (`.github/workflows/release.yml`):

- a **GitHub release**, with the changelog as its notes and the APK attached, signed with the
  release key in Cloud KMS;
- on **Google Play**, the build internal testing already has for the tagged commit goes to the
  closed testing track (`alpha`, or the repository variable `PLAY_RELEASE_TRACK`), with the
  changelog as its release notes;
- **F-Droid** builds the tag itself, reading the version from `app/build.gradle.kts` and the
  changelog from `fastlane/`.

To release, for example, version 13:

1. In `app/build.gradle.kts`, set `versionName = "13"` and raise `versionCode` (say to `801`).
2. Write the changelog, at most 500 characters, as
   `fastlane/metadata/android/en-US/changelogs/801.txt`. It is what F-Droid, Google Play and
   the GitHub release show, so it is for users: what's new, not how it was done.
3. Check both with `scripts/check_release.sh v13`, and merge them to `master` in a pull request.
4. Once the *play* workflow has run for that commit, tag it and push the tag:

   ```sh
   git tag v13 <commit> && git push origin v13
   ```

   The release fails without changing anything if the tag doesn't match `versionName`, the
   changelog is missing or too long, or the commit isn't on `master`.
5. Set `versionName = "14-dev"` for what comes next, keeping `versionCode`.

### One-time setup for releases

1. **Google Cloud.** Review [scripts/gcp_release_setup.sh](scripts/gcp_release_setup.sh) and
   run it, after `gcp_play_setup.sh`, as an admin of `clementine-data`. It creates the release
   key (a second key, not Play's upload key), lets the service account sign with it, and lets
   release tags through the provider as well as `master`.
2. **Make the release certificate** and commit it as `app/release_cert.pem`, as for the
   upload certificate below. Every GitHub release must be signed with it, so this is done
   once:

   ```sh
   $signer gencert \
     --key projects/clementine-data/locations/global/keyRings/android-signing/cryptoKeys/github-release/cryptoKeyVersions/1 \
     --subject "CN=Clementine Remote,O=Clementine" --out app/release_cert.pem
   ```

3. **Protect the tags:** a repository ruleset for `v*` tags that only maintainers can create,
   update or delete, since a tag now releases the app.
4. **Google Play:** create the closed testing track's testers list in Play Console (*Testing →
   Closed testing*). The service account's *Release apps to testing tracks* permission
   covers it; releasing to production needs *Release to production* too, and
   `PLAY_RELEASE_TRACK` set to `production`.

Until each certificate is committed, the release skips its part with a notice: without
`release_cert.pem` the GitHub release has no APK, and without `upload_cert.pem` nothing goes
to Google Play.

## Google Play internal testing

Every push to `master` builds the Play bundle and uploads it to the **internal testing**
track (`.github/workflows/play.yml`): a private channel for up to 100 testers, with no
review. The version code is the number of commits on `master`, and the version name is
`versionName` plus the commit, such as `13-dev+6ece743`.

There are no keys or credentials in the repository or its secrets:

- The **upload key** is a Cloud KMS key in the `clementine-data` project that cannot be
  exported. The bundle is built unsigned and signed by
  [kms-signer](https://github.com/clementine-player/kms-signer), which sends only digests to KMS.
  `play.yml` pins a kms-signer release by version and checksum. Play
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
   are the ones `.github/workflows/play.yml` already uses.

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
   ./gradlew bundlePlayRelease -PplayVersionCode=$(git rev-list --count HEAD)
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

5. Run the *play* workflow (*Actions → play → Run workflow*, on `master`) to check it.

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
