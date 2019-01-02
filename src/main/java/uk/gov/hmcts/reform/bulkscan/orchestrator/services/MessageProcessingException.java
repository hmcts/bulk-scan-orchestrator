package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

public class MessageProcessingException extends RuntimeException {

    public MessageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
