package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

public class DuplicateDocsException extends RuntimeException {
    public DuplicateDocsException(String message) {
        super(message);
    }
}
