package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

public class UserNotConfiguredException extends RuntimeException {
    public UserNotConfiguredException(String jurisdiction) {
        super("No configured user for jurisdiction:" + jurisdiction + " found");
    }
}
