package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;


public class UnknownClassificationException extends RuntimeException {
    public UnknownClassificationException(String message) {
        super(message);
    }
}
