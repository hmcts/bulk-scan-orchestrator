package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions;

public class ConnectionException extends RuntimeException {
    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
