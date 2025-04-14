package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

public class NotificationSendingException extends RuntimeException {

    public NotificationSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
