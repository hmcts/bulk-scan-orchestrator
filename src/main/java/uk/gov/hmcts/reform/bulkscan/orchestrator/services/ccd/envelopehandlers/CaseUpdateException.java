package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

public class CaseUpdateException extends RuntimeException {

    public CaseUpdateException(String message) {
        super(message);
    }
}
