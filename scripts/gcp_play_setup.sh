#!/usr/bin/env bash
# One-time GCP setup for signing and uploading Google Play releases. NOT run by CI or by anyone
# automatically - a human with admin rights on the clementine-data project runs this once. See
# RELEASING.md for the full picture.
#
# Reuses the Workload Identity Federation pool Clementine's macOS signing already set up
# (clementine-player/Clementine's dist/gcp_iam_setup.sh), and adds to it:
#   - An upload key in Cloud KMS that cannot be exported: releases are signed by sending it
#     digests (tools/kms-signer), so no key file ever exists
#   - A dedicated service account, the only identity that can sign with that key
#   - A provider in the existing pool that admits only this repository's master branch, and
#     lets it impersonate that service account
#   - Impersonation rights for named maintainers, so local signing (the certificate and the
#     first upload) uses the same service-account identity as CI
#
# The service account also needs access in Play Console, which only Play Console can grant:
# see RELEASING.md.
#
# Review before running: fill in MAINTAINER_EMAILS below first.
set -euo pipefail

PROJECT_ID="clementine-data"
WIF_POOL_ID="github-actions"
WIF_PROVIDER_ID="android-remote-play"
GITHUB_REPO="clementine-player/Android-Remote"
SERVICE_ACCOUNT_ID="android-play-release"
SERVICE_ACCOUNT_EMAIL="${SERVICE_ACCOUNT_ID}@${PROJECT_ID}.iam.gserviceaccount.com"
KEY_RING="android-signing"
KEY="play-upload"
KEY_LOCATION="global"

# Fill in before running: Google accounts of maintainers who may sign locally.
MAINTAINER_EMAILS=(
  "john.maguire@gmail.com"
)

echo "==> Enabling required APIs"
gcloud services enable \
  cloudkms.googleapis.com \
  androidpublisher.googleapis.com \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  --project="$PROJECT_ID"

echo "==> Creating the upload key"
# RSA 3072: Google Play accepts RSA upload keys, and at this size APK signature schemes still
# use SHA-256, the only digest Cloud KMS signs with this key (larger RSA keys switch to SHA-512).
gcloud kms keyrings create "$KEY_RING" \
  --project="$PROJECT_ID" \
  --location="$KEY_LOCATION"
gcloud kms keys create "$KEY" \
  --project="$PROJECT_ID" \
  --location="$KEY_LOCATION" \
  --keyring="$KEY_RING" \
  --purpose=asymmetric-signing \
  --default-algorithm=rsa-sign-pkcs1-3072-sha256 \
  --protection-level=hsm

echo "==> Creating the ${SERVICE_ACCOUNT_ID} service account"
gcloud iam service-accounts create "$SERVICE_ACCOUNT_ID" \
  --project="$PROJECT_ID" \
  --display-name="Android Remote Google Play releases (Cloud KMS signing + Play upload)"

echo "==> Letting the service account sign with the upload key, and nothing else"
# signerVerifier also covers reading the public key, which `kms-signer gencert` needs.
gcloud kms keys add-iam-policy-binding "$KEY" \
  --project="$PROJECT_ID" \
  --location="$KEY_LOCATION" \
  --keyring="$KEY_RING" \
  --member="serviceAccount:${SERVICE_ACCOUNT_EMAIL}" \
  --role="roles/cloudkms.signerVerifier"

echo "==> Adding a provider for ${GITHUB_REPO} to the existing ${WIF_POOL_ID} pool"
gcloud iam workload-identity-pools providers create-oidc "$WIF_PROVIDER_ID" \
  --project="$PROJECT_ID" \
  --location=global \
  --workload-identity-pool="$WIF_POOL_ID" \
  --display-name="Android Remote Play releases" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository == '${GITHUB_REPO}' && assertion.ref == 'refs/heads/master'"

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"

echo "==> Allowing ${GITHUB_REPO} to impersonate the service account"
# Only this repository's tokens carry this repository attribute, and they can only enter the
# pool through the provider above, which admits master alone.
gcloud iam service-accounts add-iam-policy-binding "$SERVICE_ACCOUNT_EMAIL" \
  --project="$PROJECT_ID" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL_ID}/attribute.repository/${GITHUB_REPO}"

if [[ ${#MAINTAINER_EMAILS[@]} -gt 0 ]]; then
  echo "==> Granting named maintainers impersonation rights on the service account"
  for email in "${MAINTAINER_EMAILS[@]}"; do
    gcloud iam service-accounts add-iam-policy-binding "$SERVICE_ACCOUNT_EMAIL" \
      --project="$PROJECT_ID" \
      --role="roles/iam.serviceAccountTokenCreator" \
      --member="user:${email}"
  done
else
  echo "==> No MAINTAINER_EMAILS set - skipping local-impersonation grants. Re-run add-iam-policy-binding manually per maintainer later:"
  echo "    gcloud iam service-accounts add-iam-policy-binding ${SERVICE_ACCOUNT_EMAIL} --project=${PROJECT_ID} --role=roles/iam.serviceAccountTokenCreator --member=user:EMAIL"
fi

cat <<EOT

==> Done. .github/workflows/play.yml already uses:
    workload_identity_provider: projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL_ID}/providers/${WIF_PROVIDER_ID}
    service_account: ${SERVICE_ACCOUNT_EMAIL}
    key: projects/${PROJECT_ID}/locations/${KEY_LOCATION}/keyRings/${KEY_RING}/cryptoKeys/${KEY}/cryptoKeyVersions/1
EOT
