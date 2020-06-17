package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition;

/**
 * Constants representing exception record field names, as per CCD definition.
 */
public final class ExceptionRecordFields {

    /* searchCaseReference contains the target case reference for the requested searchCaseReferenceType */
    public static final String SEARCH_CASE_REFERENCE = "searchCaseReference"; // ccd input field

    /* searchCaseReferenceType value can be ccdCaseReference or externalCaseReference */
    public static final String SEARCH_CASE_REFERENCE_TYPE = "searchCaseReferenceType"; // ccd input field

    /*
    attachToCaseReference contains the target case reference from the callback request.
    It will have value only when searchCaseReference and searchCaseReferenceType fields don't exist.
    attachToCaseReference field will be set to the ER case data after attaching the ER to the target case.
    */
    public static final String ATTACH_TO_CASE_REFERENCE = "attachToCaseReference"; // ccd input field

    public static final String SCANNED_DOCUMENTS = "scannedDocuments";
    public static final String EVIDENCE_HANDLED = "evidenceHandled";
    public static final String CASE_REFERENCE = "caseReference";
    public static final String DISPLAY_WARNINGS = "displayWarnings";
    public static final String OCR_DATA_VALIDATION_WARNINGS = "ocrDataValidationWarnings";
    public static final String CONTAINS_PAYMENTS = "containsPayments";
    public static final String ENVELOPE_ID = "envelopeId";
    public static final String PO_BOX_JURISDICTION = "poBoxJurisdiction";
    public static final String AWAITING_PAYMENT_DCN_PROCESSING = "awaitingPaymentDCNProcessing";
    public static final String JOURNEY_CLASSIFICATION = "journeyClassification";
    public static final String OCR_DATA = "scanOCRData";

    private ExceptionRecordFields() {
        // hiding the constructor
    }
}
