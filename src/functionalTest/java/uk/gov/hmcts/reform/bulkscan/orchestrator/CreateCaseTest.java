package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.bulkscan.orchestrator.controllers.CcdCallbackController;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.CreateExceptionRecord;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.BULK_SCANNED_CASE_TYPE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.EVENT_ID_CREATE_NEW_CASE;

// remove once master build is green
@Disabled(
    "Incompatible version in AAT makes this suite un-passable until the fix which is currently in master is deployed"
)
@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class CreateCaseTest {

    private static final String DISPLAY_WARNINGS_FIELD = "displayWarnings";
    private static final String OCR_DATA_VALIDATION_WARNINGS_FIELD = "ocrDataValidationWarnings";

    private static final String CASE_REFERENCE = "caseReference";

    @Value("${test-url}")
    private String testUrl;

    @Autowired
    private CcdApi ccdApi;

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    @Autowired
    private CcdAuthenticatorFactory ccdAuthenticatorFactory;

    private String dmUrl;

    @BeforeEach
    public void setUp() {

        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
    }

    @Test
    public void should_idempotently_create_case_from_valid_exception_record() throws Exception {
        // given
        CaseDetails exceptionRecord = createExceptionRecord("envelopes/new-envelope-create-case-with-evidence.json");

        // when
        // create case callback endpoint invoked first time
        AboutToStartOrSubmitCallbackResponse callbackResponse = invokeCallbackEndpoint(exceptionRecord);
        String caseCcdId = getCaseCcdId(callbackResponse);

        // then
        CaseDetails createdCase = ccdApi.getCase(caseCcdId, exceptionRecord.getJurisdiction());
        assertThat(createdCase.getCaseTypeId()).isEqualTo(BULK_SCANNED_CASE_TYPE);
        assertThat(createdCase.getData().get("firstName")).isEqualTo("value1");
        assertThat(createdCase.getData().get("lastName")).isEqualTo("value2");
        assertThat(createdCase.getData().get("email")).isEqualTo("hello@test.com");

        assertThat(createdCase.getData().get("bulkScanCaseReference")).isNotNull();
        String bulkScanCaseReference = (String)createdCase.getData().get("bulkScanCaseReference");
        assertThat(bulkScanCaseReference.equals(exceptionRecord.getId()));

        await("Case is ingested")
            .atMost(2, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .until(() -> caseIngested(bulkScanCaseReference));

        // when
        // create case callback endpoint invoked second time
        AboutToStartOrSubmitCallbackResponse callbackResponse2 = invokeCallbackEndpoint(exceptionRecord);
        String caseCcdId2 = getCaseCcdId(callbackResponse2);

        // then
        // the same case is returned
        assertThat(caseCcdId2).isEqualTo(caseCcdId);
        List<Long> caseIds2 = ccdApi.getCaseRefsByBulkScanCaseReference(bulkScanCaseReference, "bulkscan");
        assertThat(caseIds2.size()).isEqualTo(1);
        assertThat(caseIds2.get(0)).isEqualTo(createdCase.getId());
    }

    @Test
    public void should_clear_exception_record_warnings() throws Exception {
        // given
        CaseDetails exceptionRecord = createExceptionRecord("envelopes/new-application-with-ocr-data-warnings.json");

        // warnings are present
        assertThat(exceptionRecord).isNotNull();
        assertThat(exceptionRecord.getData()).isNotNull();
        assertThat(exceptionRecord.getData().get(DISPLAY_WARNINGS_FIELD)).isEqualTo("Yes");
        assertThat(exceptionRecord.getData().get(OCR_DATA_VALIDATION_WARNINGS_FIELD)).asList().isNotEmpty();

        // when
        AboutToStartOrSubmitCallbackResponse response = invokeCallbackEndpoint(exceptionRecord);

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
        CaseDetails exceptionRecordWithSearchFields = exceptionRecord.toBuilder().build();

        CallbackRequest callbackRequest = CallbackRequest
            .builder()
            .eventId(EVENT_ID_CREATE_NEW_CASE)
            .caseDetails(exceptionRecordWithSearchFields)
            .build();

        CcdAuthenticator ccdAuthenticator = ccdAuthenticatorFactory.createForJurisdiction("BULKSCAN");

        Response response = RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(testUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator Functional test")
            .header(AUTHORIZATION, ccdAuthenticator.getUserToken())
            .header(CcdCallbackController.USER_ID, ccdAuthenticator.getUserDetails().getId())
            .body(callbackRequest)
            .when()
            .post("/callback/create-new-case");

        return parseCcdCallbackResponse(response);
    }

    private boolean caseIngested(String bulkScanCaseReference) {
        return ccdApi.getCaseRefsByBulkScanCaseReference(bulkScanCaseReference, "bulkscan").size() == 1;
    }

    private AboutToStartOrSubmitCallbackResponse parseCcdCallbackResponse(Response response) throws IOException {
        assertThat(response.getStatusCode()).isEqualTo(200);

        final AboutToStartOrSubmitCallbackResponse callbackResponse =
            new ObjectMapper().readValue(response.getBody().asString(), AboutToStartOrSubmitCallbackResponse.class);

        return callbackResponse;
    }

    private String getCaseCcdId(AboutToStartOrSubmitCallbackResponse callbackResponse) {
        assertThat(callbackResponse.getErrors()).isEmpty();
        assertThat(callbackResponse.getData()).isNotNull();
        assertThat(callbackResponse.getData().containsKey(CASE_REFERENCE)).isTrue();
        return (String) callbackResponse.getData().get(CASE_REFERENCE);
    }

    private CaseDetails createExceptionRecord(String resourceName) throws Exception {
        // TODO use envelopeId for search
        UUID poBox = UUID.randomUUID();

        envelopeMessager.sendMessageFromFile(resourceName, "0000000000000000", null, poBox, dmUrl);

        await("Exception record is created")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> lookUpExceptionRecord(poBox).isPresent());

        CaseDetails caseDetails = lookUpExceptionRecord(poBox).get();
        return caseDetails;
    }

    private Optional<CaseDetails> lookUpExceptionRecord(UUID poBox) {
        List<CaseDetails> caseDetailsList = caseSearcher.search(
            SampleData.JURSIDICTION,
            SampleData.JURSIDICTION + "_" + CreateExceptionRecord.CASE_TYPE,
            ImmutableMap.of(
                "case.poBox", poBox.toString()
            )
        );
        return caseDetailsList.stream().findFirst();
    }
}
