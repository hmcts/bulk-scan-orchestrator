package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

class CallbackException extends RuntimeException {
    CallbackException(String errorMessage, Exception e) {
        super(errorMessage, e);
    }

    CallbackException(String errorMessage) {
        super(errorMessage);
    }
}
