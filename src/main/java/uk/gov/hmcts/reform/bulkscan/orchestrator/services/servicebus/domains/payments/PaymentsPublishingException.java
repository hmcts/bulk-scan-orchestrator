package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments;

@SuppressWarnings("serial")
public class PaymentsPublishingException extends RuntimeException {

    public PaymentsPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
