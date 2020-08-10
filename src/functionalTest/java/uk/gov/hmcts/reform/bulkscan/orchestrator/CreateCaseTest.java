package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.BULK_SCANNED_CASE_TYPE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdNewCaseCreator.EXCEPTION_RECORD_REFERENCE;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class CreateCaseTest {

    private static final String DISPLAY_WARNINGS_FIELD = "displayWarnings";
    private static final String OCR_DATA_VALIDATION_WARNINGS_FIELD = "ocrDataValidationWarnings";

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

    @ParameterizedTest()
    @ValueSource(strings = {
        "bulkscan",     // search by 'bulkScanCaseReference' (old way)
        "bulkscanauto"  // search by envelope Id (new way)
    })
    @Test
    public void should_idempotently_create_case_from_valid_exception_record(String service) throws Exception {
        // given
        String exceptionRecordResource = "envelopes/new-envelope-create-case-with-evidence-" + service + ".json";

        CaseDetails exceptionRecord = exceptionRecordCreator.createExceptionRecord(
            exceptionRecordResource,
            dmUrl
        );

        // when
        // create case callback endpoint invoked first time
        var callbackResponse = invokeCallbackEndpoint(exceptionRecord);
        String caseCcdId = getCaseCcdId(callbackResponse);

        // then
        CaseDetails createdCase = ccdApi.getCase(caseCcdId, exceptionRecord.getJurisdiction());
        assertThat(createdCase.getCaseTypeId()).isEqualTo(BULK_SCANNED_CASE_TYPE);
        assertThat(createdCase.getData().get("firstName")).isEqualTo("value1");
        assertThat(createdCase.getData().get("lastName")).isEqualTo("value2");
        assertThat(createdCase.getData().get("email")).isEqualTo("hello@test.com");

        assertThat(createdCase.getData().get(EXCEPTION_RECORD_REFERENCE)).isNotNull();
        String caseExceptionRecordReference = (String) createdCase.getData().get(EXCEPTION_RECORD_REFERENCE);
        assertThat(caseExceptionRecordReference.equals(String.valueOf(exceptionRecord.getId())));

        await("Case is ingested")
            .atMost(10, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> caseIngested(caseExceptionRecordReference, service));

        // give ElasticSearch some time to reach consistency
        Thread.sleep(2000);

        // when
        // create case callback endpoint invoked second time
        var callbackResponse2 = invokeCallbackEndpoint(exceptionRecord);
        String caseCcdId2 = getCaseCcdId(callbackResponse2);

        // then
        // the same case is returned
        assertThat(caseCcdId2).isEqualTo(caseCcdId);
        List<Long> caseIds = ccdApi.getCaseRefsByBulkScanCaseReference(caseExceptionRecordReference, service);
        assertThat(caseIds)
            .as("Should find only one service case for exception record {}", caseExceptionRecordReference)
            .hasSize(1)
            .first()
            .isEqualTo(createdCase.getId());
    }

    @Test
    public void should_clear_exception_record_warnings() throws Exception {
        // given
        CaseDetails exceptionRecord = exceptionRecordCreator.createExceptionRecord(
            "envelopes/new-application-with-ocr-data-warnings.json",
            dmUrl
        );

        // warnings are present
        assertThat(exceptionRecord).isNotNull();
        assertThat(exceptionRecord.getData()).isNotNull();
        assertThat(exceptionRecord.getData().get(DISPLAY_WARNINGS_FIELD)).isEqualTo("Yes");
        assertThat(exceptionRecord.getData().get(OCR_DATA_VALIDATION_WARNINGS_FIELD)).asList().isNotEmpty();

        // when
        var response = invokeCallbackEndpoint(exceptionRecord);

        // then
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().get(DISPLAY_WARNINGS_FIELD)).isEqualTo("No");
        assertThat(response.getData().get(OCR_DATA_VALIDATION_WARNINGS_FIELD)).asList().isEmpty();
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
            .header(CcdCallbackController.USER_ID, ccdAuthenticator.getUserDetails().getId())
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
