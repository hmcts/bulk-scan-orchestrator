package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

public class InvalidCaseIdException extends RuntimeException {
    public InvalidCaseIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
