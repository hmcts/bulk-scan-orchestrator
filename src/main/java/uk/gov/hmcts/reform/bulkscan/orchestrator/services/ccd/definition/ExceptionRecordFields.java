package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition;

/**
 * Constants representing exception record field names, as per CCD definition.
 */
public final class ExceptionRecordFields {

    public static final String SEARCH_CASE_REFERENCE = "searchCaseReference";
    public static final String SEARCH_CASE_REFERENCE_TYPE = "searchCaseReferenceType";
    public static final String ATTACH_TO_CASE_REFERENCE = "attachToCaseReference";
    public static final String SCANNED_DOCUMENTS = "scannedDocuments";
    public static final String EVIDENCE_HANDLED = "evidenceHandled";
    public static final String CASE_REFERENCE = "caseReference";
    public static final String DISPLAY_WARNINGS = "displayWarnings";
    public static final String OCR_DATA_VALIDATION_WARNINGS = "ocrDataValidationWarnings";
    public static final String CONTAINS_PAYMENTS = "containsPayments";
    public static final String ENVELOPE_ID = "envelopeId";
    public static final String PO_BOX_JURISDICTION = "poBoxJurisdiction";
    public static final String AWAITING_PAYMENT_DCN_PROCESSING = "awaitingPaymentDCNProcessing";
    public static final String OCR_DATA = "scanOCRData";

    private ExceptionRecordFields() {
        // hiding the constructor
    }
}
