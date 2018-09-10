#!/bin/bash

set -e

USER_EMAIL="${1:-bulkscan+ccd@gmail.com}"
FORENAME="${2:-Case}"
SURNAME="${3:-Worker}"
PASSWORD=Password12
USER_GROUP="caseworker"
USER_ROLES='[{"code":"caseworker-sscs"}]' # Role has to be present in idam and service related

binFolder=$(dirname "$0")

${binFolder}/create-user.sh "${USER_EMAIL}" "${FORENAME}" "${SURNAME}" "${USER_GROUP}" "${USER_ROLES}"
