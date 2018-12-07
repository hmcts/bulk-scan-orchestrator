#!/bin/bash
set -eu
chart=./bulk-scan-orchestrator/
template=$chart/values.template.yaml

export IDAM_USER_NAME=bulkscan+ccd@gmail.com
export IDAM_USER_PASSWORD=Password12
export SERVICE_NAME=$1
export IMAGE_NAME=hmcts.azurecr.io/hmcts/bulk-scan-orchestrator:latest
export SERVICE_FQDN=${SERVICE_NAME}.service.core-compute-saat.internal
export S2S_SECRET="AAAA"
export IDAM_CLIENT_SECRET="AAAA"
export IDAM_USERS_BULKSCAN_USERNAME="bulkscan+ccd@gmail.com"
export IDAM_USERS_BULKSCAN_PASSWORD="Password12"
cat $template | envsubst > $chart/values.yaml

helm dep update $chart
helm del --purge $1 || echo "No release found carrying on"
helm install --debug $chart --wait --name $1

  
