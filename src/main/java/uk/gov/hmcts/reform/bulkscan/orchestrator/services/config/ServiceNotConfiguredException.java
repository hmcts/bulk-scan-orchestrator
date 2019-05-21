package uk.gov.hmcts.reform.bulkscan.orchestrator.services.config;

public class ServiceNotConfiguredException extends RuntimeException {

    public ServiceNotConfiguredException(String message) {
        super(message);
    }
}
