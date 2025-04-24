package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

public class DuplicateDocsException extends RuntimeException {
    public DuplicateDocsException(String message) {
        super(message);
    }
}
