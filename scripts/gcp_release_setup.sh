#!/usr/bin/env bash
# One-time GCP setup for tagged releases (.github/workflows/release.yml), run after
# gcp_play_setup.sh by a human with admin rights on the clementine-data project. See
# RELEASING.md. It adds:
#   - A second key in Cloud KMS, which signs the APKs attached to GitHub releases. It is not
#     Play's upload key: that one only ever signs uploads to Play, which re-signs them.
#   - Signing rights on it for the android-play-release service account
#   - Release tags (v*) to what the repository's provider admits, besides master
set -euo pipefail

PROJECT_ID="clementine-data"
WIF_POOL_ID="github-actions"
WIF_PROVIDER_ID="android-remote-play"
GITHUB_REPO="clementine-player/Android-Remote"
SERVICE_ACCOUNT_EMAIL="android-play-release@${PROJECT_ID}.iam.gserviceaccount.com"
KEY_RING="android-signing"
KEY="github-release"
KEY_LOCATION="global"

echo "==> Creating the GitHub release key"
# RSA 3072, like the upload key: APK signature schemes use SHA-256 with it, the only digest
# Cloud KMS signs with. Every release must be signed with this key from now on.
gcloud kms keys create "$KEY" \
  --project="$PROJECT_ID" \
  --location="$KEY_LOCATION" \
  --keyring="$KEY_RING" \
  --purpose=asymmetric-signing \
  --default-algorithm=rsa-sign-pkcs1-3072-sha256 \
  --protection-level=hsm

echo "==> Letting the service account sign with it"
gcloud kms keys add-iam-policy-binding "$KEY" \
  --project="$PROJECT_ID" \
  --location="$KEY_LOCATION" \
  --keyring="$KEY_RING" \
  --member="serviceAccount:${SERVICE_ACCOUNT_EMAIL}" \
  --role="roles/cloudkms.signerVerifier"

echo "==> Admitting release tags to the ${WIF_PROVIDER_ID} provider"
# Pushing a v* tag needs write access, as pushing to master does; protect the tags with a
# repository ruleset too (RELEASING.md).
gcloud iam workload-identity-pools providers update-oidc "$WIF_PROVIDER_ID" \
  --project="$PROJECT_ID" \
  --location=global \
  --workload-identity-pool="$WIF_POOL_ID" \
  --attribute-condition="assertion.repository == '${GITHUB_REPO}' && (assertion.ref == 'refs/heads/master' || assertion.ref.startsWith('refs/tags/v'))"

cat <<EOT

==> Done. .github/workflows/release.yml already uses:
    key: projects/${PROJECT_ID}/locations/${KEY_LOCATION}/keyRings/${KEY_RING}/cryptoKeys/${KEY}/cryptoKeyVersions/1
    Next, make its certificate and commit it as app/release_cert.pem (RELEASING.md).
EOT
