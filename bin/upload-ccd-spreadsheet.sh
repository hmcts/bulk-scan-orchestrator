#!/bin/bash

if [[ ${1} = "-v" ]]; then
  export VERBOSE=true
fi

docker-compose -f docker-compose.yml up ccd-importer
