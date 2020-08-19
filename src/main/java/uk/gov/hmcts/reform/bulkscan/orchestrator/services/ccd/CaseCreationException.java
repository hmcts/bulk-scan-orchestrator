package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

public class CaseCreationException extends RuntimeException {

    public CaseCreationException(String message) {
        super(message);
    }
}
