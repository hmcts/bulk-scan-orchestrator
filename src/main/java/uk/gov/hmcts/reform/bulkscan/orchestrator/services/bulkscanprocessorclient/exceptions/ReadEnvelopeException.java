package uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.exceptions;

public class ReadEnvelopeException extends RuntimeException {

    public final String envelopeId;

    public ReadEnvelopeException(String envelopeId, Throwable cause) {
        super(cause);
        this.envelopeId = envelopeId;
    }
}
