package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

public class AlreadyAttachedToCaseException extends RuntimeException {
    public AlreadyAttachedToCaseException(String message) {
        super(message);
    }
}
