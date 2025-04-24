package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

public class CcdCallException extends RuntimeException {
    public CcdCallException(String errorMessage, Exception cause) {
        super(errorMessage, cause);
    }
}
