package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

public class InvalidMessageException extends RuntimeException {
    public InvalidMessageException(Throwable cause) {
        super(cause);
    }
}
