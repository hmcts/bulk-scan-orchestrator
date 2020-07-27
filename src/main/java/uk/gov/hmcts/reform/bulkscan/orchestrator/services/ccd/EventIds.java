package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

public final class EventIds {

    public static final String ATTACH_TO_CASE = "attachToExistingCase";
    public static final String EXTEND_CAVEAT_CASE = "extendCaveatCase";
    public static final String EXTEND_BULK_SCAN_CASE = "extendBulkScanCase";
    public static final String CREATE_NEW_CASE = "createNewCase";
    public static final String ATTACH_SCANNED_DOCS = "attachScannedDocs";
    public static final String ATTACH_SCANNED_DOCS_WITH_OCR = "attachScannedDocsWithOcr";

    private EventIds() {
        // utility class constructor
    }
}
