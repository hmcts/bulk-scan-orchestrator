#!/bin/sh

set -e

USER_EMAIL=ccd-importer@server.net
FORENAME=CCD
SURNAME=Importer
PASSWORD=Password12
USER_GROUP=ccd-import

curl -XPOST -H 'Content-Type: application/json' http://idam-api:8080/testing-support/accounts -d '{
    "email": "'${USER_EMAIL}'",
    "forename": "'${FORENAME}'",
    "surname": "'${SURNAME}'",
    "levelOfAccess": 0,
    "userGroup": {
      "code": "'${USER_GROUP}'"
    },
    "roles": [{ "code": "ccd-import" }],
    "activationDate": "",
    "lastAccess": "",
    "password": "'${PASSWORD}'"
}'

echo -e "Created user with:\nUsername: ${USER_EMAIL}\nPassword:${PASSWORD}\nFirstname: ${FORENAME}\nSurname: ${SURNAME}\nUser group: ${USER_GROUP}"

