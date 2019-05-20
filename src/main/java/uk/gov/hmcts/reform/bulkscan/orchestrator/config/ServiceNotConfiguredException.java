package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

public class ServiceNotConfiguredException extends RuntimeException {

    public ServiceNotConfiguredException(String message) {
        super(message);
    }
}
