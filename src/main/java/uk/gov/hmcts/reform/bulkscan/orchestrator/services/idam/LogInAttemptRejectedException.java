package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

public class LogInAttemptRejectedException extends Exception {

    public LogInAttemptRejectedException(String message) {
        super(message);
    }
}
