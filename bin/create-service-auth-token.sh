#!/bin/sh
## Create service auth token for the micro service
##
##  Usage: ./create-service-auth-token.sh [microservice_name]
##
## Options:
##    - microservice_name: Name of the microservice. Default to `bulk_scan_orchestrator`.
##
## Returns a valid IDAM service token for the given microservice.


MICROSERVICE="${1:-bulk_scan_orchestrator}"

curl --silent -H "Content-Type: application/json" http://localhost:4552/testing-support/lease -d '{ "microservice": "'${MICROSERVICE}'"}'
