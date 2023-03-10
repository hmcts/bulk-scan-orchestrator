#!/usr/bin/env bash

# Script to create a .env file
# Format of command: sudo ./create-env-file.sh <key vault> <service name (in the chart yaml)>
# Example of use: sudo ./create-env-file.sh bulk-scan-aat bulk-scan-orchestrator
# Author/contact for updating: Adam Stevenson

# Refresh env file by removing one if it currently exists
rm .localenv

KEY_VAULT="${1}"
SERVICE_NAME="${2}"

SECRETS=$(yq eval ".java.keyVaults.${KEY_VAULT}.secrets[]" ../charts/"${SERVICE_NAME}"/values.yaml)
SECRETS=${SECRETS//alias: /}
SECRETS=${SECRETS//name: /}
SECRETS_AS_ARRAY=("${x//\n/}")
readarray -t SECRETS_AS_ARRAY <<<"$SECRETS"

KEY_VAULT="${KEY_VAULT}-aat"

function fetch_secret_from_keyvault() {
    local SECRET_NAME=$1

    az keyvault secret show --vault-name "${KEY_VAULT}" --name "${SECRET_NAME}" --query "value"
}

function store_secret_from_keyvault() {
    local SECRET_VAR=$1
    local SECRET_NAME=$2

    SECRET_VALUE=$(fetch_secret_from_keyvault "${SECRET_NAME}")
    store_secret "${SECRET_VAR}" "${SECRET_VALUE}"
}

function store_secret() {
    local SECRET_VAR=$1
    local SECRET_VALUE=$2
    local SECRET_TO_WRITE="${SECRET_VAR}=${SECRET_VALUE}"

    echo "${SECRET_TO_WRITE}"
    echo "${SECRET_TO_WRITE}" >> .localenv
}

echo "# ----------------------- "
echo "# Populating secrets to .localenv file from ${KEY_VAULT} on ""$(date)"
LENGTH="${#SECRETS_AS_ARRAY[@]}"
for ((i=0; i <= LENGTH-1; i+=2)) do

  # Secret env name. Substitute dots and -'s with _'s
  ENV_NAME="${SECRETS_AS_ARRAY[$((i+1))]}"
  ENV_NAME=${ENV_NAME^^}
  ENV_NAME=$(echo "${ENV_NAME}" | tr . _)
  ENV_NAME=$(echo "${ENV_NAME}" | tr - _)

  # Secret env value
  ENV_VALUE=${SECRETS_AS_ARRAY[${i}]}

  # Retrieve secret from keyvault
  store_secret_from_keyvault "${ENV_NAME}" "${ENV_VALUE}"
done
echo "# End of fetched secrets. "
echo "# ----------------------- "
