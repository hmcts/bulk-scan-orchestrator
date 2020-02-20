package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

public class UnsupportedJurisdictionException extends RuntimeException {

    public UnsupportedJurisdictionException(String message) {
        super(message);
    }
}
