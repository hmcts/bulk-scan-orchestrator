#!/usr/bin/env bash

# Main script for setting up environment
# Format of command: sudo ./setup-env.sh <key vault> <service name (in the chart yaml)> <env>
# Example of use: sudo ./setup-env.sh bulk-scan bulk-scan-orchestrator aat
# Author/contact for updating: Adam Stevenson
KEY_VAULT="${1}"
SERVICE_NAME="${2}"
ENV="${3}"

sudo ./create-env-file.sh "${KEY_VAULT}" "${SERVICE_NAME}" "${ENV}"

while true; do
    read -p "Do you wish to run this in docker? Yy or Nn: " yn
    case $yn in
        [Yy]* )
          docker-compose down -v
          docker-compose build
          docker-compose up -d
          sed 's/localhost/host.docker.internal/' substitutions.json
          echo "Setup complete! You can manage these services now via normal docker commands. Double check all is well with docker-compose ps"
          break;;
        [Nn]* )
          sed 's/host.docker.internal/localhost/' substitutions.json
          echo "Setup complete! Next step is to add the .localenv file through the ENV plugin and run the application afterwards"
          exit;;
    esac
done
