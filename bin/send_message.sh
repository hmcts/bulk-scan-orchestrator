#!/usr/bin/env bash
set -eu
file_name=${1:-"example1.json"}
environment=${2:-"sandbox"}
file="../src/test/resources/${file_name}"
echo "using file: ${file_name} environment:${environment}"
shared_access_signature="SharedAccessSignature sr=sb%3A%2F%2Fbulk-scan-servicebus-sandbox.servicebus.windows.net%2F&sig=Cz5XsYG8tGYUkY0nwrtO%2Fkxc2YOPTsoDyrJ%2BwctPWEU%3D&se=1538398077&skn=SendSharedAccessKey"
cat ${file}

curl -v \
 -d @${file}\
 -H "Authorization: ${shared_access_signature}"\
 -H "Content-Type: application/json"\
 "https://bulk-scan-servicebus-${environment}.servicebus.windows.net/envelopes/messages"

echo
