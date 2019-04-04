package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

public class AlreadyAttachedToCaseException extends RuntimeException {
    public AlreadyAttachedToCaseException(String message) {
        super(message);
    }
}
