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
`versionName` plus the commit, such as `13-dev+6ece743`.

There are no keys or credentials in the repository or its secrets:

- The **upload key** is a Cloud KMS key that cannot be exported. The bundle is built unsigned
  and signed by [tools/kms-signer](tools/kms-signer/README.md), which sends only digests to
  KMS. Play re-signs the app with its own app signing key (Play App Signing).
- GitHub Actions authenticates to Google Cloud with **Workload Identity Federation**: a
  short-lived token that only runs on `master` of this repository can use. The same identity
  signs with the key and uploads to Play.

Until the setup below is done, the workflow only builds the unsigned bundle.

### One-time setup

Replace `PROJECT` with a Google Cloud project ID and `PROJECT_NUMBER` with its number.

1. **Enable the APIs.**

   ```sh
   gcloud services enable cloudkms.googleapis.com iamcredentials.googleapis.com \
       sts.googleapis.com androidpublisher.googleapis.com --project PROJECT
   ```

2. **Create the upload key.** Add `--protection-level hsm` to keep it in a hardware module.

   ```sh
   gcloud kms keyrings create android-signing --location global --project PROJECT
   gcloud kms keys create upload-key --keyring android-signing --location global \
       --purpose asymmetric-signing --default-algorithm rsa-sign-pkcs1-3072-sha256 \
       --project PROJECT
   ```

   The key version is
   `projects/PROJECT/locations/global/keyRings/android-signing/cryptoKeys/upload-key/cryptoKeyVersions/1`.

3. **Create the service account** CI acts as, and let it sign with this key only:

   ```sh
   gcloud iam service-accounts create play-release --project PROJECT
   gcloud kms keys add-iam-policy-binding upload-key --keyring android-signing \
       --location global --project PROJECT \
       --member serviceAccount:play-release@PROJECT.iam.gserviceaccount.com \
       --role roles/cloudkms.signerVerifier
   ```

4. **Trust GitHub Actions** on `master` of this repository to act as it:

   ```sh
   gcloud iam workload-identity-pools create github --location global --project PROJECT
   gcloud iam workload-identity-pools providers create-oidc github-actions \
       --workload-identity-pool github --location global --project PROJECT \
       --issuer-uri https://token.actions.githubusercontent.com \
       --attribute-mapping google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref \
       --attribute-condition "assertion.repository == 'clementine-player/Android-Remote' && assertion.ref == 'refs/heads/master'"
   gcloud iam service-accounts add-iam-policy-binding \
       play-release@PROJECT.iam.gserviceaccount.com --project PROJECT \
       --role roles/iam.workloadIdentityUser \
       --member principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/clementine-player/Android-Remote
   ```

5. **Make the signing certificate** and commit it as `app/upload_cert.pem`. This needs
   `roles/cloudkms.signerVerifier` and `roles/cloudkms.publicKeyViewer` on the key for your own
   account, through `gcloud auth application-default login`:

   ```sh
   ./gradlew -p tools/kms-signer installDist
   tools/kms-signer/build/install/kms-signer/bin/kms-signer gencert \
       --key projects/PROJECT/locations/global/keyRings/android-signing/cryptoKeys/upload-key/cryptoKeyVersions/1 \
       --subject "CN=Clementine Remote Upload,O=Clementine" --out app/upload_cert.pem
   ```

6. **Create the app** in [Play Console](https://play.google.com/console): *Create app*, name
   *Clementine Remote*, app, free. Under *Testing → Internal testing*, create an email list of
   testers and add it to the track. Play only accepts API uploads for an app after its first
   bundle was uploaded in the console, so sign one locally and upload it by hand:

   ```sh
   ./gradlew bundlePlayRelease -PplayVersionCode=$(git rev-list --count HEAD)
   tools/kms-signer/build/install/kms-signer/bin/kms-signer sign \
       --key projects/PROJECT/locations/global/keyRings/android-signing/cryptoKeys/upload-key/cryptoKeyVersions/1 \
       --cert app/upload_cert.pem --min-sdk 23 \
       --in app/build/outputs/bundle/playRelease/ClementineRemote-play-release.aab \
       --out ClementineRemote-play-release-signed.aab
   ```

   This upload also enrols the app in Play App Signing with a Google-generated app signing
   key, and registers `upload_cert.pem` as its upload certificate. Roll the release out to
   internal testing and share the opt-in link with testers.

7. **Let the service account upload:** in Play Console, *Users and permissions → Invite new
   users*, invite `play-release@PROJECT.iam.gserviceaccount.com` with *Release apps to testing
   tracks* for this app.

8. **Set the repository variables** (*Settings → Secrets and variables → Actions →
   Variables*; none of these are secret):

   | Variable                          | Value                                                                              |
   |-----------------------------------|------------------------------------------------------------------------------------|
   | `GCP_WORKLOAD_IDENTITY_PROVIDER`  | `projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/github-actions` |
   | `GCP_SERVICE_ACCOUNT`             | `play-release@PROJECT.iam.gserviceaccount.com`                                     |
   | `ANDROID_KMS_KEY`                 | the key version from step 2                                                        |

   If Play rejects uploads with "Only releases with status draft may be created on draft
   app", the first release has not been rolled out yet (step 6): either do that, or set the
   variable `PLAY_RELEASE_STATUS` to `draft` and roll each release out by hand.

9. Run the *play* workflow (*Actions → play → Run workflow*, on `master`) to check it.

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
