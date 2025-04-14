package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

public class InvalidConfigurationException extends RuntimeException {
    public InvalidConfigurationException(String msg) {
        super(msg);
    }
}
