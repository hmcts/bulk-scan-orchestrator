package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.json.JSONArray;
import org.json.JSONObject;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.CcdAuthInfo;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.CcdCaseRetriever.CASE_TYPE_ID;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.NEW_APPLICATION;

public interface TestData {
    String SSCS_TOKEN = "SSCS_TOKEN";
    String USER_TOKEN = "USER_token";
    String USER_NAME = "USER_NAME";
    String PASSWORD = "PASSWORD";
    String USER_ID = "USER_ID";
    String CASE_REF = "ABC123";
    String JURSIDICTION = "SSCS";
    long CASE_ID = 23L;

    UserDetails USER_DETAILS = new UserDetails(USER_ID, null, null, null, emptyList());
    Credential USER_CREDS = new Credential(USER_NAME, PASSWORD);
    CcdAuthInfo CCD_AUTH_INFO = new CcdAuthInfo(SSCS_TOKEN, USER_CREDS, USER_TOKEN, USER_DETAILS);

    CaseDetails THE_CASE = CaseDetails.builder()
        .id(CASE_ID)
        .jurisdiction(JURSIDICTION)
        .caseTypeId(CASE_TYPE_ID).build();


    static String envelopeJson() throws Exception {
        return new JSONObject()
            .put("id", "eb9c3598-35fc-424e-b05a-902ee9f11d56")
            .put("case_ref", CASE_REF)
            .put("jurisdiction", JURSIDICTION)
            .put("classification", NEW_APPLICATION)
            .put("zip_file_name", "zip-file-test.zip")
            .put("doc_urls", new JSONArray(asList("a", "b")))
            .toString();
    }

}
