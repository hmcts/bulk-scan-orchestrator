package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

public class PaymentsPublishingException extends RuntimeException {

    public PaymentsPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
