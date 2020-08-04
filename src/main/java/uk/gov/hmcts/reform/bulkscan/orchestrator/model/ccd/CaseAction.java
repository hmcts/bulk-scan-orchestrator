package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

/**
 * Action that was done on a case.
 *
 * <p>Values from this enum are used in CCD case definitions ('action' field of BulkScanEnvelope complex type)</p>
 */
public enum CaseAction {
    CREATE("create"),
    UPDATE("update");

    public final String value;

    CaseAction(String value) {
        this.value = value;
    }
}
