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

- It takes the **store screenshots**: the app on an emulator, against a real Clementine
  (`.github/workflows/store-screenshots.yml`).
- It makes a **release commit** on top of `master` that sets the version, writes the notes
  as the fastlane changelog and adds the screenshots, and pushes it as the tag `v<version>`.
  Nothing is pushed to `master`, which keeps its `-dev` version.
- **F-Droid** builds that tag, reading the version and changelog from it.
- A **GitHub release** gets every note, and the APK, signed with the release key in Cloud KMS.
- **Google Play** gets the bundle on production (or the repository variable
  `PLAY_RELEASE_TRACK`, which takes one track or several separated by commas), with the notes
  as its release notes: at most 500 characters, so a long list ends with "And more fixes and
  improvements." Play's store listing gets the release's screenshots. The testing tracks get
  every development build instead (see *Development builds*). A release's version code is
  higher than its commit's development build, so testers get the release too, until the
  next development build. Play Console warns then that the testing tracks' build is
  "shadowed" by production: that's expected.

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
   both. For production: get production access (Play Console's dashboard says if it's
   needed), choose its countries (*Production → Countries/regions*), and give the service
   account *Release to production* too. Until then, set `PLAY_RELEASE_TRACK` to `beta`, or
   releases fail at the upload. Once the first production release is live, point README.md's
   Google Play badge at the store page,
   `https://play.google.com/store/apps/details?id=org.clementine_player.remote`, instead of
   open testing's.
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
- on its **closed testing** track, for the testers on its list, and its **open testing**
  track, the beta anyone can join from the store page. Unlike internal testing, each build
  there goes through Google's review first, which is usually quick for testing tracks;
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

Play sometimes wants changes sent for review from Play Console rather than through the API,
such as after it rejects an update, and refuses to send them itself ("Changes cannot be sent
for review automatically"). Uploads and the store listing then go to Play unsent, with a
warning on the run: send them for review from *Publishing overview* in Play Console. Once Play
has reviewed them, it sends the next upload's changes itself again.

### Before testing more widely

Closed or open testing and production go through review, which needs the *App content*
section of Play Console completed:

- **Privacy policy:** link to [PRIVACY.md](PRIVACY.md).
- **Data safety:** no data collected or shared.
- **Foreground services:** the service uses the `connectedDevice` type to stay connected to
  Clementine while music plays, and `mediaPlayback` while Clementine plays on the phone; Play
  asks for a description and a short video of each.
- **Phone state:** `READ_PHONE_STATE` is used to lower the volume during calls.

New personal developer accounts must also run a closed test with at least 12 testers for
14 days before production access is granted.

## Translations

The app is translated on [Transifex](https://app.transifex.com/davidsansome/clementine-remot/),
next to the iOS remote, and `.github/workflows/translations.yml` keeps the two in step, as
Clementine's own workflow does, with no manual steps:

- When the English strings (`app/src/main/res/values/strings.xml`) change on `master`, they're
  pushed to Transifex for the translators.
- Every night, the translations are pulled back (only translated strings: the rest show in
  English), reviewed and not reviewed yet alike (`scripts/android-translations.py` merges the
  two, as Transifex's modes for Android files each leave one out), checked with lint, and committed to `master` when they changed, as
  "Automatic merge of translations from Transifex". That commit has a release note, so new
  translations make the next weekly release; however many nights they changed, the release
  notes say so once.
- A pull that would remove more than a tenth of a language's translations, or of all of them,
  fails instead of committing: that means Transifex didn't give them (a wrong resource in
  `.tx/config`, or step 3 below not done yet).
- Transifex fills in strings whose English it has translated already, for this app or the iOS
  remote (its translation memory fill-up): the same wording in both apps is translated once.

Which files the Transifex resource maps to is in `.tx/config`. A language that's new on
Transifex gets its folder from `scripts/android-language-folders.sh`, which turns Transifex's
`zh_CN` into Android's `values-zh-rCN`. Translations are made on Transifex, not in pull
requests: the next pull would overwrite them.

**One-time setup:**

1. **Transifex:** in the project's settings, turn on translation memory fill-up. Make an API
   token (*User settings → API token*) and add it as the repository secret `TX_TOKEN`. The
   resource and its languages needn't be made by hand: the first push makes the resource, and
   step 3 adds every language the app has.
2. **GitHub:** make a deploy key with write access (*Settings → Deploy keys*), add its private
   half as the secret `TX_KEY`, and if `master`'s branch protection or rulesets would refuse
   the push, let deploy keys bypass them. It pushes as itself so that `ci.yml` runs on the
   commit; the workflow's own token can't start other workflows.
3. **The first time,** run the workflow by hand with *Push translations* ticked: it adds the
   app's languages to the project and sends the repository's translations up to Transifex, so
   translators start from them, then pulls. Do this before the iOS remote's first run: its
   String Catalog is uploaded as one file, which only fills in languages the project has.

## Store listing

`fastlane/metadata/android/en-US/` is the store listing for both stores: `title.txt`,
`short_description.txt`, `full_description.txt`, and `images/` (the icon and the feature
graphic, which `scripts/store_graphics.py` draws). F-Droid reads it from each release tag.

The **phone screenshots** are each release's own, so they're only on release tags, not on
`master`: every release takes them, and puts them in its release commit
(`scripts/store_screenshots.py`), so the listing always shows the app as released. They're
the main screens in the dark theme, which most people use, then the player in the light
theme. A screen that looks the same as in the last release stays the same file. If taking
them fails, the release goes ahead with the last release's, with a warning.

Google Play gets the text, icon and feature graphic from `.github/workflows/play-listing.yml`,
whenever they change on `master` or when the workflow is run by hand, and the whole listing,
screenshots too, from each release (`scripts/play_listing.py`). It goes to the app's default
language in Play Console, whichever that is. Only what differs from Play's listing is
changed, so the listing goes to review only when it has changed. On pull requests, the
workflow checks the listing against Play's limits instead: text lengths and image sizes.

Pull requests that change the UI get the *store-screenshots* workflow's screenshots as a
comment, next to the latest release's, so a change to how the listing will look is seen in
review.

**One-time setup:** in Play Console, *Users and permissions*, give
`android-play-release@clementine-data.iam.gserviceaccount.com` *Edit store listing, pricing
& distribution* for this app, as well as *Release apps to testing tracks*.

## Demo Clementine for store reviewers

The remotes are no use without a Clementine, so store reviewers get one on the internet:
`demo.clementine-player.org`, port 5500, playing the showcase library's works (the store
screenshots' library) in real recordings: the public-domain and CC0 recordings from Wikimedia
Commons in `clementine-it/demo-library.tsv`, downloaded and checked when the image is built. The
screenshots keep their generated tones. The iOS remote's App Store reviewers use it too, so its
address and auth code are in both stores' review notes.

- `.github/workflows/demo-image.yml` builds the image, `clementine-it` with the demo library
  (`LIBRARY=demo`) and Clementine's latest release, and pushes it to
  `ghcr.io/clementine-player/clementine-demo`. It runs when `clementine-it` changes, and weekly
  for new Clementine releases.
- It runs on an e2-micro VM in the Google Cloud project `clementine-remote-demo`, which
  Compute Engine's free tier covers. The project has nothing else in it, and the VM no service
  account, so it holds no credentials. `scripts/demo-cloud-init.yaml` runs the container, with
  host networking, and replaces it with a fresh one from the newest image every night, so
  whatever a visitor changed is gone.
- Clementine's auth code is the only thing keeping strangers out, so it's random, kept in the
  VM's metadata (`clementine-auth-code`), and never committed. The demo has no saved radio
  streams (`SAVED_RADIO=0`): it would relay SomaFM's stations to anyone.
- A budget alerts at half and all of $5 a month: the free tier only covers 1 GB of traffic
  out a month.

To look at it: `gcloud compute ssh clementine-demo --project clementine-remote-demo --zone
us-central1-a --tunnel-through-iap`, then `docker logs clementine`. To replace the container
now: `sudo systemctl restart clementine-demo`.

### One-time setup for the demo

1. Run the *demo-image* workflow (*Actions → demo-image → Run workflow*), then make the
   `clementine-demo` package public in its settings on GitHub, so the VM can pull it without
   credentials.
2. Review [scripts/gcp_demo_setup.sh](scripts/gcp_demo_setup.sh) and run it, with the ID of
   Clementine's billing account (`gcloud billing accounts list`):

   ```sh
   BILLING_ACCOUNT=<billing account ID> scripts/gcp_demo_setup.sh
   ```

   It prints the VM's addresses and its auth code.
3. In Cloudflare's DNS for `clementine-player.org`, add `A` and `AAAA` records for `demo` with
   those addresses, with the proxy off (*DNS only*): Cloudflare's proxy doesn't pass the
   remote's port.
4. Give reviewers the address and the auth code: in Play Console, *App content → App access*;
   for the iOS remote, its repository's `DEMO_AUTH_CODE` secret, which its release adds to
   the App Review notes.
5. Check it from a phone on mobile data: connect, browse the library, download a song, and play
   on the phone.

Playing on the phone needs Clementine to send stream URLs the phone can reach from outside
Clementine's network: the VM's address is behind Google Cloud's NAT.
