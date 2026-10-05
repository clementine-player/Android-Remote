#!/usr/bin/env bash
# One-time GCP setup for the demo Clementine: a Clementine on the internet, playing the showcase
# library, for app store reviewers to try both remotes with (demo.clementine-player.org). NOT
# run by CI or by anyone automatically - a human with rights to create projects and link them to
# Clementine's billing account runs this once. See RELEASING.md.
#
#   BILLING_ACCOUNT=<billing account ID> scripts/gcp_demo_setup.sh
#
# Creates, in a project of its own (nothing else in it, so a compromised VM reaches nothing):
#   - an e2-micro VM in us-central1, which Compute Engine's free tier covers, running
#     Container-Optimized OS with no service account, so it holds no credentials. It runs the
#     clementine-demo image (.github/workflows/demo-image.yml) as scripts/demo-cloud-init.yaml
#     says.
#   - static IPv4 and IPv6 addresses, so the address reviewers are given never changes. App
#     Review tests on IPv6-only networks.
#   - firewall rules: port 5500 from anywhere, SSH only through Identity-Aware Proxy
#   - a random auth code, kept in the VM's metadata, and printed for the store listings' review
#     notes. It is never committed.
#   - a monthly budget with alerts: the free tier only covers 1 GB of traffic out a month.
#
# Running it again changes nothing that's there, except that it applies the VM's startup config
# again (scripts/demo-cloud-init.yaml); reset the VM to use it. Review before running.
set -euo pipefail
# gcloud never asks anything: a question would wait unseen where exists() hides the output.
export CLOUDSDK_CORE_DISABLE_PROMPTS=1

PROJECT_ID="clementine-remote-demo"
REGION="us-central1"
ZONE="us-central1-a"
NETWORK="demo"
SUBNET="demo-us-central1"
VM="clementine-demo"
IPV4_ADDRESS="clementine-demo-ipv4"
IPV6_ADDRESS="clementine-demo-ipv6"
BUDGET_USD=5
HOSTNAME="demo.clementine-player.org"

: "${BILLING_ACCOUNT:?Set BILLING_ACCOUNT to the billing account ID (gcloud billing accounts list)}"
cd "$(dirname "$0")/.."

exists() { "$@" > /dev/null 2>&1; }

echo "==> Project $PROJECT_ID"
if ! exists gcloud projects describe "$PROJECT_ID"; then
  gcloud projects create "$PROJECT_ID" --name="Clementine Remote demo"
fi
gcloud billing projects link "$PROJECT_ID" --billing-account="$BILLING_ACCOUNT"
gcloud services enable compute.googleapis.com billingbudgets.googleapis.com --project="$PROJECT_ID"
gc() { gcloud --project="$PROJECT_ID" "$@"; }
# A newly enabled Compute Engine API can take a few minutes to answer.
for i in $(seq 30); do
  exists gc compute regions describe "$REGION" && break
  [ "$i" = 30 ] && { echo "Compute Engine isn't answering; run this again later." >&2; exit 1; }
  echo "Waiting for Compute Engine..."
  sleep 10
done

echo "==> Network, with IPv6"
if ! exists gc compute networks describe "$NETWORK"; then
  gc compute networks create "$NETWORK" --subnet-mode=custom
fi
if ! exists gc compute networks subnets describe "$SUBNET" --region="$REGION"; then
  gc compute networks subnets create "$SUBNET" --network="$NETWORK" --region="$REGION" \
    --range=10.0.0.0/24 --stack-type=IPV4_IPV6 --ipv6-access-type=EXTERNAL
fi

echo "==> Firewall"
if ! exists gc compute firewall-rules describe demo-remote-ipv4; then
  gc compute firewall-rules create demo-remote-ipv4 --network="$NETWORK" \
    --allow=tcp:5500 --source-ranges=0.0.0.0/0 --target-tags=clementine-demo
fi
if ! exists gc compute firewall-rules describe demo-remote-ipv6; then
  gc compute firewall-rules create demo-remote-ipv6 --network="$NETWORK" \
    --allow=tcp:5500 --source-ranges=::/0 --target-tags=clementine-demo
fi
# Identity-Aware Proxy's range: gcloud compute ssh --tunnel-through-iap.
if ! exists gc compute firewall-rules describe demo-ssh-iap; then
  gc compute firewall-rules create demo-ssh-iap --network="$NETWORK" \
    --allow=tcp:22 --source-ranges=35.235.240.0/20 --target-tags=clementine-demo
fi

echo "==> Static addresses"
if ! exists gc compute addresses describe "$IPV4_ADDRESS" --region="$REGION"; then
  gc compute addresses create "$IPV4_ADDRESS" --region="$REGION"
fi
if ! exists gc compute addresses describe "$IPV6_ADDRESS" --region="$REGION"; then
  gc compute addresses create "$IPV6_ADDRESS" --region="$REGION" --subnet="$SUBNET" \
    --ip-version=IPV6 --endpoint-type=VM
fi
ipv4=$(gc compute addresses describe "$IPV4_ADDRESS" --region="$REGION" --format='value(address)')
ipv6=$(gc compute addresses describe "$IPV6_ADDRESS" --region="$REGION" --format='value(address)')

echo "==> VM $VM"
if ! exists gc compute instances describe "$VM" --zone="$ZONE"; then
  # 10000-99999: Clementine's auth code is a number, so no leading zeros to lose.
  auth_code=$(( 10000 + $(od -An -N4 -tu4 /dev/urandom | tr -d ' ') % 90000 ))
  gc compute instances create "$VM" --zone="$ZONE" \
    --machine-type=e2-micro \
    --image-family=cos-stable --image-project=cos-cloud \
    --boot-disk-type=pd-standard --boot-disk-size=10GB \
    --subnet="$SUBNET" --stack-type=IPV4_IPV6 \
    --address="$ipv4" --external-ipv6-address="$ipv6" --external-ipv6-prefix-length=96 \
    --ipv6-network-tier=PREMIUM \
    --no-service-account --no-scopes \
    --shielded-secure-boot \
    --tags=clementine-demo \
    --metadata=clementine-auth-code="$auth_code" \
    --metadata-from-file=user-data=scripts/demo-cloud-init.yaml
else
  gc compute instances add-metadata "$VM" --zone="$ZONE" \
    --metadata-from-file=user-data=scripts/demo-cloud-init.yaml
fi
auth_code=$(gc compute instances describe "$VM" --zone="$ZONE" \
  --format='value(metadata.items.clementine-auth-code)')

echo "==> Budget"
if ! gcloud billing budgets list --billing-account="$BILLING_ACCOUNT" \
    --format='value(displayName)' | grep -qx "Clementine Remote demo"; then
  project_number=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
  gcloud billing budgets create --billing-account="$BILLING_ACCOUNT" \
    --display-name="Clementine Remote demo" --budget-amount="${BUDGET_USD}USD" \
    --filter-projects="projects/$project_number" \
    --threshold-rule=percent=0.5 --threshold-rule=percent=1.0
fi

cat << EOF

Done. Still to do by hand (see RELEASING.md):

1. In Cloudflare's DNS for clementine-player.org, add these records, with the proxy off
   ("DNS only": Cloudflare's proxy doesn't pass the remote's port):
     A     ${HOSTNAME%%.*}   $ipv4
     AAAA  ${HOSTNAME%%.*}   $ipv6
2. The auth code is $auth_code. Give it, with $HOSTNAME, to the stores' reviewers:
   - iOS: the iOS-Remote repository's secret DEMO_AUTH_CODE
       gh secret set DEMO_AUTH_CODE --repo clementine-player/iOS-Remote --body $auth_code
   - Google Play: Play Console, App content, App access
3. Once the clementine-demo image is public on GitHub's container registry, check it from a
   phone on mobile data: $HOSTNAME, auth code $auth_code.
EOF
