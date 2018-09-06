# Bulk Scan Orchestrator

[![Build Status](https://travis-ci.org/hmcts/bulk-scan-orchestrator.svg?branch=master)](https://travis-ci.org/hmcts/bulk-scan-orchestrator)

## Purpose

The purpose of this application is to retrieve information about envelopes uploaded to Bulk Scanning
(via Azure Service Bus queue) and manage the related CCD cases.

## Building and deploying the application

The project uses [Gradle](https://gradle.org) as a build tool. It already contains
`./gradlew` wrapper script, so there's no need to install gradle.

### Building the application

To build the project execute the following command:

```bash
  ./gradlew build
```

### Running the application

Create the image of the application by executing the following command:

```bash
  ./gradlew assemble
```

Application listens on port `8582` which can be overridden by setting `SERVER_PORT` environment variable or from [.env](/.env) file.

### Running smoke tests

```bash
  ./gradlew smoke
```

### Running functional tests

```bash
  ./gradlew functional
```

### Running integration tests

```bash
  ./gradlew integration
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
