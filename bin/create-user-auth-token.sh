#!/bin/sh
## Create auth token for the user

##
## Usage: ./create-user-auth-token.sh [role] [user_id]
##
## Options:
##    - role: Role assigned to user in generated token. Default to `ccd-import`.
##    - user_id: ID assigned to user in generated token. Default to `1`.
##
## Returns a valid IDAM user token for the given role and user_id.

ROLE="${1:-ccd-import}"
USER_ID="${2:-1}"

curl --silent http://localhost:8080/testing-support/lease -F id="${USER_ID}" -Frole="${ROLE}"
