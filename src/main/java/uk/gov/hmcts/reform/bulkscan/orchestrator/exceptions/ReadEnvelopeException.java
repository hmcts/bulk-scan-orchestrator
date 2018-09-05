package uk.gov.hmcts.reform.bulkscan.orchestrator.exceptions;

public class ReadEnvelopeException extends RuntimeException {
    public ReadEnvelopeException(Throwable cause) {
        super(cause);
    }
}
