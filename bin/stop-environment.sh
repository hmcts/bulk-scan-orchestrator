#!/bin/sh

COMPOSE_FILES="-f docker-compose.yml"
docker-compose ${COMPOSE_FILES} down ${@}

