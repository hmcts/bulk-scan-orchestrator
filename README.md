# bulk-scan-orchestrator

[![Build Status](https://travis-ci.org/hmcts/bulk-scan-orchestrator.svg?branch=master)](https://travis-ci.org/hmcts/bulk-scan-orchestrator)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/e9272daf4b714e4f95280916e763b6b2)](https://www.codacy.com/app/HMCTS/bulk-scan-orchestrator)

## Local development

In order to start the app you need to set the following properties:
```yaml
idam:
  users:
    sscs:
      username: bulkscanorchestrator+systemupdate@gmail.com
      password: Password12

```
They can be set in `src/main/resources/application-default.yaml`

These can also be set as environment variables:
```bash
IDAM_USERS_SSCS_USERNAME=bulkscanorchestrator+systemupdate@gmail.com
IDAM_USERS_SSCS_PASSWORD=Password12
```
