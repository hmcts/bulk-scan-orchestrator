package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

public class ConnectionException extends RuntimeException {
    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
