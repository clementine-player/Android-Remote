# kms-signer

Signs the Google Play release bundle with an upload key held in Cloud KMS. The private key
never exists outside Cloud KMS, not in CI, not on a developer machine, and not while making
the signing certificate. Adapted from hatstand/nowt's `mobile/kms-signer`.

## How it works

`apksig`, the library `apksigner` and the Android Gradle plugin use, has an extension point
for keys held elsewhere: `KeyConfig.Kms` and `com.android.apksig.kms.KmsSignerEngineProvider`,
found through `ServiceLoader`. It defines `KmsType.GCP` but ships no implementation; this tool
is that implementation. apksig builds the signature files exactly as it would for a local key
and hands over only the bytes to sign. `GcpKmsSignerEngine` hashes them locally and sends the
SHA-256 digest to Cloud KMS's `AsymmetricSign`, with its CRC32C integrity checks both ways.
Only a digest and a signature cross the wire.

Google Play takes app bundles, which carry a plain JAR signature (APK signature schemes v2 and
v3 only apply to APKs, and Play re-signs those itself with the app signing key). For a `.aab`
the tool enables JAR signing only; a bundle has no top-level manifest for apksig to read the
minimum SDK from, so `--min-sdk` gives it. APKs get JAR signing and schemes v2 and v3.

The key, created by `scripts/gcp_play_setup.sh`, is `RSA_SIGN_PKCS1_3072_SHA256`. Keys must be
RSA of at most 3072 bits or `EC_SIGN_P256_SHA256`: Cloud KMS keys sign SHA-256 digests only,
and APK signature schemes use SHA-512 with RSA keys larger than 3072 bits.

Authentication is Application Default Credentials: Workload Identity Federation in CI
(`google-github-actions/auth`, no stored keys), and locally `gcloud auth
application-default login --impersonate-service-account=android-play-release@clementine-data.iam.gserviceaccount.com`.

## Usage

Build it with the repository's wrapper:

```sh
./gradlew -p tools/kms-signer installDist
signer=tools/kms-signer/build/install/kms-signer/bin/kms-signer
```

**Once per key**, make the signing certificate and commit it. Every release must be signed with
the same certificate, so it is never regenerated:

```sh
$signer gencert \
  --key projects/clementine-data/locations/global/keyRings/android-signing/cryptoKeys/play-upload/cryptoKeyVersions/1 \
  --subject "CN=Clementine Remote Upload,O=Clementine" \
  --out app/upload_cert.pem
```

**Each release**, sign the unsigned bundle and check the result:

```sh
$signer sign --key projects/.../cryptoKeyVersions/1 --cert app/upload_cert.pem \
  --in app/build/outputs/bundle/playRelease/ClementineRemote-play-release.aab \
  --out ClementineRemote-play-release-signed.aab --min-sdk 23
$signer verify --in ClementineRemote-play-release-signed.aab
```

## Tests

`./gradlew -p tools/kms-signer test` signs bundles through apksig's KMS extension point with a
local key standing in for Cloud KMS, and checks them with the JDK's JAR verification and
`jarsigner`.
