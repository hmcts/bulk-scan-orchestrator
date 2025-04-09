package uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions;

@SuppressWarnings("serial")
public class PaymentsPublishingException extends RuntimeException {

    public PaymentsPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
