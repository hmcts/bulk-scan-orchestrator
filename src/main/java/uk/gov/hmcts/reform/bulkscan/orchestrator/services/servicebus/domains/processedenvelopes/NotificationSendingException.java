package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

public class NotificationSendingException extends RuntimeException {

    public NotificationSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
