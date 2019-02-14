package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

public class CcdApiException extends RuntimeException {

    private final boolean doThrow;

    CcdApiException(String errorMessage, boolean doThrow, Exception e) {
        super(errorMessage, e);

        this.doThrow = doThrow;
    }

    CcdApiException(String errorMessage, boolean doThrow) {
        super(errorMessage);

        this.doThrow = doThrow;
    }

    public boolean toThrow() {
        return doThrow;
    }
}
