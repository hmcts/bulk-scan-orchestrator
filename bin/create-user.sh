#!/bin/bash

set -e

USER_EMAIL="${1:-me@server.net}"
FORENAME="${2:-John}"
SURNAME="${3:-Smith}"
PASSWORD=Password12
USER_GROUP="${4:-bsp-systemupdate}"
USER_ROLES="${5:-[]}"

curl -XPOST -H 'Content-Type: application/json' http://localhost:8080/testing-support/accounts -d '{
    "email": "'${USER_EMAIL}'",
    "forename": "'${FORENAME}'",
    "surname": "'${SURNAME}'",
    "levelOfAccess": 0,
    "userGroup": {
      "code": "'${USER_GROUP}'"
    },
    "activationDate": "",
    "lastAccess": "",
    "roles": '${USER_ROLES}',
    "password": "'${PASSWORD}'"
}'

echo -e "Created user with:\nUsername: ${USER_EMAIL}\nPassword:${PASSWORD}\nFirstname: ${FORENAME}\nSurname: ${SURNAME}\nUser group: ${USER_GROUP}\nRoles: ${USER_ROLES}"
