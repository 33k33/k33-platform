#!/usr/bin/env bash

#
#  Script to deploy k33-backend to GCP cloud run.
#

set -e

# checking bash version
if [ -z "${BASH_VERSINFO}" ] || [ -z "${BASH_VERSINFO[0]}" ] || [ ${BASH_VERSINFO[0]} -lt 4 ]; then
  echo "This script requires Bash version >= 4"
  exit 1
fi

# init env vars from .env.gcp
#if [ -f .env.gcp ]; then
#  set -o allexport
#  source .env.gcp
#  set +o allexport
#fi

# loading secrets from 1password
if [[ $# != 1 || "$1" != "dev" && "$1" != "prod" ]]; then
  echo "Usage: $0 (dev|prod)"
  exit 1
fi

ENV=$1

GCP_PROJECT_ID=$(op read op://env/$ENV/gcp/GCP_PROJECT_ID)
# stripe
STRIPE_PRODUCT_ID_RESEARCH_PRO=$(op read op://env/$ENV/stripe/STRIPE_PRODUCT_ID_RESEARCH_PRO)
STRIPE_COUPON_CORPORATE_PLAN=$(op read op://env/$ENV/stripe/STRIPE_COUPON_CORPORATE_PLAN)
# slack
SLACK_ALERTS_CHANNEL_ID=$(op read op://env/$ENV/slack/SLACK_ALERTS_CHANNEL_ID)
SLACK_GENERAL_CHANNEL_ID=$(op read op://env/$ENV/slack/SLACK_GENERAL_CHANNEL_ID)
SLACK_INVEST_CHANNEL_ID=$(op read op://env/$ENV/slack/SLACK_INVEST_CHANNEL_ID)
SLACK_PRODUCT_CHANNEL_ID=$(op read op://env/$ENV/slack/SLACK_PRODUCT_CHANNEL_ID)
SLACK_PROFESSIONAL_INVESTORS_CHANNEL_ID=$(op read op://env/$ENV/slack/SLACK_PROFESSIONAL_INVESTORS_CHANNEL_ID)
SLACK_RESEARCH_CHANNEL_ID=$(op read op://env/$ENV/slack/SLACK_RESEARCH_CHANNEL_ID)
SLACK_RESEARCH_EVENTS_CHANNEL_ID=$(op read op://env/$ENV/slack/SLACK_RESEARCH_EVENTS_CHANNEL_ID)
# sendgrid
SENDGRID_TEMPLATE_ID_WELCOME_TO_K33=$(op read op://env/$ENV/sendgrid/SENDGRID_TEMPLATE_ID_WELCOME_TO_K33)
SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH=$(op read op://env/$ENV/sendgrid/SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH)
SENDGRID_UNSUBSCRIBE_GROUP_ID_K33=$(op read op://env/$ENV/sendgrid/SENDGRID_UNSUBSCRIBE_GROUP_ID_K33)
SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH=$(op read op://env/$ENV/sendgrid/SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH)
## TWIC
SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_TWIC=$(op read op://env/$ENV/sendgrid/SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_TWIC)
SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_TWIC=$(op read op://env/$ENV/sendgrid/SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_TWIC)
SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_TWIC=$(op read op://env/$ENV/sendgrid/SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_TWIC)
## NN
SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_NN=$(op read op://env/$ENV/sendgrid/SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_NN)
SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_NN=$(op read op://env/$ENV/sendgrid/SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_NN)
SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_NN=$(op read op://env/$ENV/sendgrid/SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_NN)
## AOC
SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_AOC=$(op read op://env/$ENV/sendgrid/SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_AOC)
SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_AOC=$(op read op://env/$ENV/sendgrid/SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_AOC)
SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_AOC=$(op read op://env/$ENV/sendgrid/SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_AOC)
## PRO
SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_PRO_TRIAL=$(op read op://env/$ENV/sendgrid/SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_PRO_TRIAL)
SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_PRO=$(op read op://env/$ENV/sendgrid/SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_PRO)
SENDGRID_TEMPLATE_ID_CANCEL_DURING_TRIAL_K33_RESEARCH_PRO=$(op read op://env/$ENV/sendgrid/SENDGRID_TEMPLATE_ID_CANCEL_DURING_TRIAL_K33_RESEARCH_PRO)
SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_PRO=$(op read op://env/$ENV/sendgrid/SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_PRO)
SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_PRO=$(op read op://env/$ENV/sendgrid/SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_PRO)
# ga
GOOGLE_ANALYTICS_FIREBASE_APP_ID=$(op read op://env/$ENV/analytics/GOOGLE_ANALYTICS_FIREBASE_APP_ID)
GOOGLE_ANALYTICS_MEASUREMENT_ID=$(op read op://env/$ENV/analytics/GOOGLE_ANALYTICS_MEASUREMENT_ID)
# invest
INVEST_DENIED_COUNTRY_CODE_LIST=$(op read op://env/$ENV/invest/INVEST_DENIED_COUNTRY_CODE_LIST)
INVEST_EMAIL_FROM=$(op read op://env/$ENV/invest/INVEST_EMAIL_FROM)
INVEST_EMAIL_TO_LIST=$(op read op://env/$ENV/invest/INVEST_EMAIL_TO_LIST)
INVEST_EMAIL_CC_LIST=$(op read op://env/$ENV/invest/INVEST_EMAIL_CC_LIST)
INVEST_EMAIL_BCC_LIST=$(op read op://env/$ENV/invest/INVEST_EMAIL_BCC_LIST)


declare -A backendCloudRun
backendCloudRun["service"]="k33-backend"
backendCloudRun["image"]="europe-docker.pkg.dev/${GCP_PROJECT_ID}/backend/k33-backend:$(git rev-parse HEAD | cut -c 1-12)"
backendCloudRun["service_account"]="k33-backend"

echo "Build with Gradle"
./gradlew :apps:k33-backend:installDist --parallel

echo "Building and pushing docker image: ${backendCloudRun["image"]}"
docker image build --platform linux/amd64 -t "${backendCloudRun["image"]}" apps/k33-backend
docker image push "${backendCloudRun["image"]}"

echo "Deploying to cloud run: ${backendCloudRun["image"]}"
gcloud run deploy "${backendCloudRun["service"]}" \
  --region europe-west1 \
  --image "${backendCloudRun["image"]}" \
  --cpu=1 \
  --memory=1Gi \
  --min-instances=1 \
  --max-instances=1 \
  --concurrency=1000 \
  --set-env-vars=GCP_PROJECT_ID="${GCP_PROJECT_ID}" \
  --set-env-vars=GOOGLE_CLOUD_PROJECT="${GCP_PROJECT_ID}" \
  --set-env-vars=STRIPE_PRODUCT_ID_RESEARCH_TWIC="${STRIPE_PRODUCT_ID_RESEARCH_TWIC}" \
  --set-env-vars=STRIPE_PRODUCT_ID_RESEARCH_NN="${STRIPE_PRODUCT_ID_RESEARCH_NN}" \
  --set-env-vars=STRIPE_PRODUCT_ID_RESEARCH_AOC="${STRIPE_PRODUCT_ID_RESEARCH_AOC}" \
  --set-env-vars=STRIPE_PRODUCT_ID_RESEARCH_PRO="${STRIPE_PRODUCT_ID_RESEARCH_PRO}" \
  --set-env-vars=STRIPE_COUPON_CORPORATE_PLAN="${STRIPE_COUPON_CORPORATE_PLAN}" \
  --set-env-vars=SLACK_ALERTS_CHANNEL_ID="${SLACK_ALERTS_CHANNEL_ID}" \
  --set-env-vars=SLACK_GENERAL_CHANNEL_ID="${SLACK_GENERAL_CHANNEL_ID}" \
  --set-env-vars=SLACK_INVEST_CHANNEL_ID="${SLACK_INVEST_CHANNEL_ID}" \
  --set-env-vars=SLACK_PRODUCT_CHANNEL_ID="${SLACK_PRODUCT_CHANNEL_ID}" \
  --set-env-vars=SLACK_PROFESSIONAL_INVESTORS_CHANNEL_ID="${SLACK_PROFESSIONAL_INVESTORS_CHANNEL_ID}" \
  --set-env-vars=SLACK_RESEARCH_CHANNEL_ID="${SLACK_RESEARCH_CHANNEL_ID}" \
  --set-env-vars=SLACK_RESEARCH_EVENTS_CHANNEL_ID="${SLACK_RESEARCH_EVENTS_CHANNEL_ID}" \
  --set-env-vars=SENDGRID_TEMPLATE_ID_WELCOME_TO_K33="${SENDGRID_TEMPLATE_ID_WELCOME_TO_K33}" \
  --set-env-vars=SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH="${SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH}" \
  --set-env-vars=SENDGRID_UNSUBSCRIBE_GROUP_ID_K33="${SENDGRID_UNSUBSCRIBE_GROUP_ID_K33}" \
  --set-env-vars=SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH="${SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH}" \
  --set-env-vars=SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_TWIC="${SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_TWIC}" \
  --set-env-vars=SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_TWIC="${SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_TWIC}" \
  --set-env-vars=SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_TWIC="${SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_TWIC}" \
  --set-env-vars=SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_NN="${SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_NN}" \
  --set-env-vars=SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_NN="${SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_NN}" \
  --set-env-vars=SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_NN="${SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_NN}" \
  --set-env-vars=SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_AOC="${SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_AOC}" \
  --set-env-vars=SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_AOC="${SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_AOC}" \
  --set-env-vars=SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_AOC="${SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_AOC}" \
  --set-env-vars=SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_PRO_TRIAL="${SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_PRO_TRIAL}" \
  --set-env-vars=SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_PRO="${SENDGRID_TEMPLATE_ID_WELCOME_TO_K33_RESEARCH_PRO}" \
  --set-env-vars=SENDGRID_TEMPLATE_ID_CANCEL_DURING_TRIAL_K33_RESEARCH_PRO="${SENDGRID_TEMPLATE_ID_CANCEL_DURING_TRIAL_K33_RESEARCH_PRO}" \
  --set-env-vars=SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_PRO="${SENDGRID_CONTACT_LIST_ID_K33_RESEARCH_PRO}" \
  --set-env-vars=SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_PRO="${SENDGRID_UNSUBSCRIBE_GROUP_ID_K33_RESEARCH_PRO}" \
  --set-env-vars=GOOGLE_ANALYTICS_FIREBASE_APP_ID="${GOOGLE_ANALYTICS_FIREBASE_APP_ID}" \
  --set-env-vars=GOOGLE_ANALYTICS_MEASUREMENT_ID="${GOOGLE_ANALYTICS_MEASUREMENT_ID}" \
  --set-env-vars=^:^INVEST_DENIED_COUNTRY_CODE_LIST="${INVEST_DENIED_COUNTRY_CODE_LIST}" \
  --set-env-vars=INVEST_EMAIL_FROM="${INVEST_EMAIL_FROM}" \
  --set-env-vars=^:^INVEST_EMAIL_TO_LIST="${INVEST_EMAIL_TO_LIST}" \
  --set-env-vars=^:^INVEST_EMAIL_CC_LIST="${INVEST_EMAIL_CC_LIST}" \
  --set-env-vars=^:^INVEST_EMAIL_BCC_LIST="${INVEST_EMAIL_BCC_LIST}" \
  --service-account "${backendCloudRun["service_account"]}" \
  --no-allow-unauthenticated \
  --port=8080 \
  --platform=managed
