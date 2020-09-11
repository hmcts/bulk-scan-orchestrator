package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

public class UnableToAttachDocumentsException extends RuntimeException {

    public UnableToAttachDocumentsException(String message, Throwable cause) {
        super(message, cause);
    }
}
