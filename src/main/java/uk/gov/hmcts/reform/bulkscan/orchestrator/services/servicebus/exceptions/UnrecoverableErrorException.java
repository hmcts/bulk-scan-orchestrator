package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions;

public class UnrecoverableErrorException extends RuntimeException {

    public UnrecoverableErrorException(String message) {
        super(message);
    }
}
