package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions;

public class MessageProcessingException extends RuntimeException {

    public MessageProcessingException(String message) {
        super(message);
    }
}
