# Releasing

The app is built in two flavors that differ only in their application ID:

| Flavor   | Application ID                 | Distributed through                |
|----------|--------------------------------|------------------------------------|
| `fdroid` | `de.qspool.clementineremote`   | F-Droid, GitHub Releases           |
| `play`   | `org.clementine_player.remote` | Google Play                        |

Google Play keeps `de.qspool.clementineremote` reserved for the original author's account,
so the Play build needs its own ID. F-Droid keeps the original so existing installs upgrade.

## Google Play internal testing

Every push to `master` builds the Play bundle and uploads it to the **internal testing**
track (`.github/workflows/play.yml`): a private channel for up to 100 testers, with no
review. The version code is the number of commits on `master`, and the version name is
`versionName` plus the commit, such as `13-dev+6ece743`. Until the secrets below are set, the
workflow only builds the bundle and keeps it as a workflow artifact.

### One-time setup

1. **Upload key.** Create a key for signing uploads. Play re-signs the app with its own key
   (Play App Signing), so this key can be reset through Play support if it is lost.

   ```sh
   keytool -genkeypair -v -keystore upload.jks -alias upload \
       -keyalg RSA -keysize 4096 -validity 10000
   ```

   Keep `upload.jks` and its passwords somewhere safe, outside the repository.

2. **Create the app** in [Play Console](https://play.google.com/console): *Create app*, name
   *Clementine Remote*, app, free. Then under *Testing → Internal testing*:
   - Create an email list of testers and add it to the track.
   - Build a signed bundle locally and upload it by hand. Play only accepts API uploads
     for an app after its first bundle has been uploaded in the console; this upload also
     sets the package name and enrols the app in Play App Signing.

     ```sh
     SIGNING_KEYSTORE=$PWD/upload.jks SIGNING_KEYSTORE_PASSWORD=… \
     SIGNING_KEY_ALIAS=upload SIGNING_KEY_PASSWORD=… \
     ./gradlew bundlePlayRelease -PplayVersionCode=$(git rev-list --count HEAD)
     # app/build/outputs/bundle/playRelease/ClementineRemote-play-release.aab
     ```

   - Roll the release out to internal testing, and share the opt-in link with testers.

3. **Service account** for uploads from GitHub Actions:
   - In Google Cloud, create a project (or reuse one), enable the *Google Play Android
     Developer API*, create a service account and download a JSON key for it.
   - In Play Console, *Users and permissions → Invite new users*, invite the service
     account's email and give it *Release apps to testing tracks* for this app.

4. **GitHub secrets** (*Settings → Secrets and variables → Actions*):

   | Secret                       | Value                                  |
   |------------------------------|----------------------------------------|
   | `SIGNING_KEYSTORE_BASE64`    | output of `base64 -w0 upload.jks`      |
   | `SIGNING_KEYSTORE_PASSWORD`  | the keystore password                  |
   | `SIGNING_KEY_ALIAS`          | `upload`                               |
   | `SIGNING_KEY_PASSWORD`       | the key password                       |
   | `PLAY_SERVICE_ACCOUNT_JSON`  | contents of the service account key    |

   If Play rejects uploads with "Only releases with status draft may be created on draft
   app", the app has not been rolled out once yet (step 2): either do that, or set the
   repository variable `PLAY_RELEASE_STATUS` to `draft` and roll each release out by hand.

5. Run the *play* workflow (*Actions → play → Run workflow*) to check the upload.

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
