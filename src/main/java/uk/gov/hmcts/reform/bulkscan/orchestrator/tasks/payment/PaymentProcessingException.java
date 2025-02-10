package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment;

public class PaymentProcessingException extends RuntimeException {
    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
