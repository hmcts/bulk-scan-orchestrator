package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

class CallbackException extends RuntimeException {
    CallbackException(String error, Exception e) {
        super(error, e);
    }
}
