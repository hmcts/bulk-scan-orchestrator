package uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam;

public class AccountLockedException extends Exception {

    public AccountLockedException(String message) {
        super(message);
    }
}
