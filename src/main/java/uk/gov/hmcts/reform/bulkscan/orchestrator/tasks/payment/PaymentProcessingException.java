package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment;

/**
 * Exception to be thrown for when there is a failure in processing payments.
 * For example, you would expect this to be thrown if calling Bulk Scan Payment Processor
 * was unsuccessful.
 */
public class PaymentProcessingException extends RuntimeException {
    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
