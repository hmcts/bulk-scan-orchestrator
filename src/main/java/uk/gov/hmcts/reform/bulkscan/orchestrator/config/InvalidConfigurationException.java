package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

public class InvalidConfigurationException extends RuntimeException {
    public InvalidConfigurationException(String msg) {
        super(msg);
    }
}
