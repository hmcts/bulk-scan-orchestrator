package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

public class DuplicateDocsException extends RuntimeException {
    public DuplicateDocsException(String message) {
        super(message);
    }
}
