#!/bin/sh

COMPOSE_FILES="-f docker-compose.yml"
docker-compose ${COMPOSE_FILES} up ${@} -d ccd-case-management-web \
                                           ccd-api-gateway \
                                           idam-api \
                                           authentication-web \
                                           smtp-server
