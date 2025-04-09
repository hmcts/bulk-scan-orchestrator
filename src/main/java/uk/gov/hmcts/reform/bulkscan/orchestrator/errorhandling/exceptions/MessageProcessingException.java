package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

public class MessageProcessingException extends RuntimeException {

    public MessageProcessingException(String message) {
        super(message);
    }
}
