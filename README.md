# bulk-scan-orchestrator

[![Build Status](https://travis-ci.org/hmcts/bulk-scan-orchestrator.svg?branch=master)](https://travis-ci.org/hmcts/bulk-scan-orchestrator)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/e9272daf4b714e4f95280916e763b6b2)](https://www.codacy.com/app/HMCTS/bulk-scan-orchestrator)

## Running end to end using docker
- Run up the docker environment from bulk-scan-shared-infrastructure
- You will have to setup these environment variables on either your
run configuration or bash shell
```
IDAM_USERS_BULKSCAN_USERNAME = bulkscan+ccd@gmail.com
IDAM_USERS_BULKSCAN_PASSWORD = Password12
```
- either using the environment vars or application default you will need to set
  ```
  queue:
    envelopes:
      connection-string: XXXXX
      queue-name: YYYY
  ```
  
  - where: 
    - XXXX is the connection string from azure to the queue you intend to use.
    - YYYY is the name of the queue
  
  
- add a case into ccd using the [case management ui](http://localhost:3451)
- copy the case number from the UI (excluding the # and -'s) and place it into the example1.json#case_ref to reference the created case.
- get the secret and run the getSasSecret script to create the queue jwt token.
- put this in the send_message.sh script
- run the send_message script in and make sure the 201 success is returned.
- Voila ... you should see the debug of your service retrieving the message and processing it.
