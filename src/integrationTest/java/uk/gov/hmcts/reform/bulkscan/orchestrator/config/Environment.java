package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

/**
 * This Singleton contains all the environmental items needed in the integration test profile.
 */
public final class Environment {

    private static final String USER_ID = "640";
    public static final String JURISDICTION = "BULKSCAN";
    public static final String CASE_TYPE_BULK_SCAN = "Bulk_Scanned";
    public static final String CASE_TYPE_EXCEPTION_RECORD = "BULKSCAN_ExceptionRecord";
    public static final String CASE_REF = "1539007368674134";

    private static final String CASE_TYPE_URL = String.format(
        "/caseworkers/%s/jurisdictions/%s/case-types/%s",
        USER_ID,
        JURISDICTION,
        CASE_TYPE_BULK_SCAN
    );
    private static final String CASE_URL = String.format(
        "%s/cases/%s",
        CASE_TYPE_URL,
        CASE_REF
    );
    public static final String GET_CASE_URL = "/cases/" + CASE_REF;
    public static final String CASE_EVENT_URL = CASE_URL + "/events";
    public static final String CASE_SUBMIT_URL = CASE_TYPE_URL + "/cases";
    public static final String CASE_EVENT_TRIGGER_START_URL = CASE_TYPE_URL + "/event-triggers/createException/token";

    private Environment() {
        // utility class construct
    }
}
