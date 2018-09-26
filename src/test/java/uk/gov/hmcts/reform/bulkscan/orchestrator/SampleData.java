package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.json.JSONArray;
import org.json.JSONObject;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.CcdAuthInfo;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.CcdCaseRetriever.CASE_TYPE_ID;

public class SampleData {
    public static final String SERVICE_TOKEN = "SERVICE_TOKEN";
    public static final String USER_TOKEN = "USER_token";
    public static final String USER_NAME = "USER_NAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String USER_ID = "USER_ID";
    public static final String CASE_REF = "ABC123";
    public static final String JURSIDICTION = "SSCS";
    public static final long CASE_ID = 23L;

    public static final UserDetails USER_DETAILS = new UserDetails(USER_ID,
        null, null, null, emptyList());
    public static final Credential USER_CREDS = new Credential(USER_NAME, PASSWORD);
    public static final CcdAuthInfo CCD_AUTH_INFO = new CcdAuthInfo(SERVICE_TOKEN,
        USER_TOKEN, USER_DETAILS, JURSIDICTION);

    public static final CaseDetails THE_CASE = CaseDetails.builder()
        .id(CASE_ID)
        .jurisdiction(JURSIDICTION)
        .caseTypeId(CASE_TYPE_ID)
        .build();

    public static String envelopeJson() throws Exception {
        return new JSONObject()
            .put("id", "eb9c3598-35fc-424e-b05a-902ee9f11d56")
            .put("case_ref", CASE_REF)
            .put("jurisdiction", JURSIDICTION)
            .put("zip_file_name", "zip-file-test.zip")
            .put("classification", Classification.NEW_APPLICATION)
            .put("doc_urls", new JSONArray(asList("a", "b")))
            .toString();
    }

    private SampleData() {
        // util class
    }
}
