#!/bin/bash

set -e

USERNAME="${1:-me@server.net}"
PASSWORD="${2:-Password12}"
USER_ROLE="${3:-bulkscan-new-features-consent-given}"
IDAM_URI=http://localhost:8080
REDIRECT_URI=http://localhost:3000/receiver
CLIENT_SECRET=123456

userToken=$(./docker/ccd-definition-import/scripts/idam-authenticate.sh ${USERNAME} ${PASSWORD} ${IDAM_URI} ${REDIRECT_URI} ${CLIENT_SECRET})

curl -XPOST -H 'Content-Type: application/json' -H "Authorization: Bearer ${userToken}" http://localhost:4400/user/roles -d '{
    "role": "'${USER_ROLE}'"
   }'

echo -e "Created user roles with role: ${USER_ROLE} for Username:${USERNAME}"
