package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

public class CallbackException extends RuntimeException {

    private final boolean doThrow;

    CallbackException(String errorMessage, boolean doThrow, Exception e) {
        super(errorMessage, e);

        this.doThrow = doThrow;
    }

    CallbackException(String errorMessage, boolean doThrow) {
        super(errorMessage);

        this.doThrow = doThrow;
    }

    public boolean toThrow() {
        return doThrow;
    }
}
