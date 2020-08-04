package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition;

/**
 * Names of the fields used by Bulk Scan in service CCD case definitions.
 */
public final class CommonServiceCaseFields {

    private CommonServiceCaseFields() {
        // hiding the constructor - this class is for constants only
    }

    // reference to the exception record
    public static final String BULK_SCAN_CASE_REFERENCE = "bulkScanCaseReference";

    // a collection of references to envelopes that affected the case
    public static final String BULK_SCAN_ENVELOPES = "bulkScanEnvelopes";
}
