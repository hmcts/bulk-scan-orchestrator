package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.exceptions;

public class CcdClientException extends RuntimeException {

    public CcdClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
