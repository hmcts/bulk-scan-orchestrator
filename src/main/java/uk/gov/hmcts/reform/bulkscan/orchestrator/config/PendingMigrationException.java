package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

public class PendingMigrationException extends RuntimeException {

    private static final long serialVersionUID = -3892580157302271206L;

    public PendingMigrationException(String message) {
        super(message);
    }
}
