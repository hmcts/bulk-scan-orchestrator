package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

public class NoUserConfiguredException extends RuntimeException {
    public NoUserConfiguredException(String jurisdiction) {
        super("No user configured for jurisdiction: " + jurisdiction);
    }
}
