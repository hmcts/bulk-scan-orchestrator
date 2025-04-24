package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
