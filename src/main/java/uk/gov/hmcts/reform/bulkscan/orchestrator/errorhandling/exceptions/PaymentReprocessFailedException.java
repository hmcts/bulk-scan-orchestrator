package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

/**
 * Custom exception which handles when a payment has failed reprocessing.
 */
public class PaymentReprocessFailedException extends RuntimeException {

    /**
     * Constructor for custom PaymentReprocessFailedException.
     *
     * @param message error message.
     */
    public PaymentReprocessFailedException(String message) {
        super(message);
    }
}
