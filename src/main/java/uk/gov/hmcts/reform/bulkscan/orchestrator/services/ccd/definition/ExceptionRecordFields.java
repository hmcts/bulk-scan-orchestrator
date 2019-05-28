package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition;

/**
 * Constants representing exception record field names, as per CCD definition.
 */
public final class ExceptionRecordFields {

    public static final String SEARCH_CASE_REFERENCE = "searchCaseReference";
    public static final String SEARCH_CASE_REFERENCE_TYPE = "searchCaseReferenceType";
    public static final String ATTACH_TO_CASE_REFERENCE = "attachToCaseReference";

    private ExceptionRecordFields() {
        // hiding the constructor
    }
}
