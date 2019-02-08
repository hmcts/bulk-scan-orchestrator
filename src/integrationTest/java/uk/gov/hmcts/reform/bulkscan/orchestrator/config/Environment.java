package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

/**
 * This Singleton contains all the environmental items needed in the integration test profile.
 */
public final class Environment {

    public static final String USER_ID = "640";
    public static final String JURIDICTION = "BULKSCAN";
    public static final String CASE_TYPE_BULK_SCAN = "Bulk_Scanned";
    public static final String CASE_TYPE_EXCEPTION_RECORD = "BULKSCAN_ExceptionRecord";
    public static final String CASE_REF = "1539007368674134";

    private static final String caseTypeUrl = String.format(
        "/caseworkers/%s/jurisdictions/%s/case-types/%s",
        USER_ID,
        JURIDICTION,
        CASE_TYPE_BULK_SCAN
    );
    private static final String caseUrl = String.format(
        "%s/cases/%s",
        caseTypeUrl,
        CASE_REF
    );
    public static final String getCaseUrl = "/cases/" + CASE_REF;
    public static final String caseEventUrl = caseUrl + "/events";
    public static final String caseSubmitUrl = caseTypeUrl + "/cases";
    public static final String caseEventTriggerStartUrl = caseTypeUrl + "/event-triggers/createException/token";

    private Environment() {
        // utility class construct
    }
}
