package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

public class MessageSendingException extends RuntimeException {

    public MessageSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
