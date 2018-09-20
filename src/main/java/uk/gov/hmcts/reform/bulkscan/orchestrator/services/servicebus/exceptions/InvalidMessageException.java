package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions;

public class InvalidMessageException extends RuntimeException {
    public InvalidMessageException(Throwable cause) {
        super(cause);
    }
}
