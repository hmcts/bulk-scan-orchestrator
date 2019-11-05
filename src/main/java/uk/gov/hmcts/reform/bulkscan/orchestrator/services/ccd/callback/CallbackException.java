package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback;

// General exception which we do not want to handle and respond to callback with 500
public class CallbackException extends RuntimeException {

    private static final long serialVersionUID = -1612149638676262395L;

    public CallbackException(String message) {
        super(message);
    }

    public CallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
