#!/bin/bash
set -eu
chart=bulk-scan-orchestrator/
template=$chart/values.template.yaml

export IDAM_USER_NAME=bulkscan+ccd@gmail.com
export IDAM_USER_PASSWORD=Password12
export SERVICE_NAME=$1
export IMAGE_NAME=hmcts.azurecr.io/bulk-scan-orchestrator@sha256:85dd5e1b1054c68aaa5e356ee07de2e0d9be5b1540d82b8466d3f2106ace3c9a
export SERVICE_FQDN=${SERVICE_NAME}.service.core-compute-saat.internal
export S2S_SECRET="AAAA"
export IDAM_CLIENT_SECRET="AAAA"
export IDAM_USERS_BULKSCAN_USERNAME="bulkscan+ccd@gmail.com"
export IDAM_USERS_BULKSCAN_PASSWORD="Password12"
cat $template | envsubst > $chart/values.yaml

helm dep update charts/bulk-scan-orchestrator/
helm del --purge $1 || echo "No release found carrying on"
helm install --debug charts/bulk-scan-orchestrator/ --name $1

  
