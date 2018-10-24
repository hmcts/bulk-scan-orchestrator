package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

/**
 * Temporary enumerator for case types until CCD client is updated.
 */
public enum CaseTypeId {

    BULK_SCANNED("Bulk_Scanned"),
    EXCEPTION_RECORD("ExceptionRecord");

    private final String id;

    CaseTypeId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
