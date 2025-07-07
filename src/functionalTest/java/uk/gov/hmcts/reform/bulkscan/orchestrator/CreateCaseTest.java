package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.CcdCallbackController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ExceptionRecordCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIds;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.io.IOException;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class CreateCaseTest {

    private static final String DISPLAY_WARNINGS_FIELD = "displayWarnings";
    private static final String OCR_DATA_VALIDATION_WARNINGS_FIELD = "ocrDataValidationWarnings";
    private static final String BULK_SCAN_CASE_REFERENCE_FIELD = "bulkScanCaseReference";

    private static final String CASE_REFERENCE = "caseReference";

    @Value("${test-url}") String testUrl;

    @Autowired ExceptionRecordCreator exceptionRecordCreator;
    @Autowired CcdApi ccdApi;
    @Autowired DocumentManagementUploadService dmUploadService;
    @Autowired CcdAuthenticatorFactory ccdAuthenticatorFactory;

    String dmUrl;

    @BeforeEach
    public void setUp() {

        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
    }

    /**
     * Hits the services callback endpoint with a request to create case upon an exception record.
     */
    private AboutToStartOrSubmitCallbackResponse invokeCallbackEndpoint(
        CaseDetails exceptionRecord
    ) throws IOException {

        CcdAuthenticator ccdAuthenticator = ccdAuthenticatorFactory.createForJurisdiction("BULKSCAN");

        Response response = RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(testUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator Functional test")
            .header(AUTHORIZATION, ccdAuthenticator.getUserToken())
            .header(CcdCallbackController.USER_ID, ccdAuthenticator.getUserId())
            .body(
                CallbackRequest
                    .builder()
                    .eventId(EventIds.CREATE_NEW_CASE)
                    .caseDetails(exceptionRecord)
                    .build()
            )
            .when()
            .post("/callback/create-new-case");

        assertThat(response.getStatusCode()).isEqualTo(200);

        return new ObjectMapper().readValue(response.getBody().asString(), AboutToStartOrSubmitCallbackResponse.class);
    }

    private boolean caseIngested(String bulkScanCaseReference, String service) {
        return ccdApi.getCaseRefsByBulkScanCaseReference(bulkScanCaseReference, service).size() == 1;
    }

    private String getCaseCcdId(AboutToStartOrSubmitCallbackResponse callbackResponse) {
        assertThat(callbackResponse.getErrors()).isEmpty();
        assertThat(callbackResponse.getData()).isNotNull();
        assertThat(callbackResponse.getData().containsKey(CASE_REFERENCE)).isTrue();
        return (String) callbackResponse.getData().get(CASE_REFERENCE);
    }
}
