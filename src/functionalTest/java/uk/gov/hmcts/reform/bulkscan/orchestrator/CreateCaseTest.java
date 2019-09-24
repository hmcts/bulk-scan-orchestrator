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
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
@Disabled
class CreateCaseTest {

    @Value("${test-url}")
    private String testUrl;

    @Value("${document_management.url}")
    private String documentManagementUrl;

    @Value("${document_management.context-path}")
    private String dmContextPath;

    @Autowired
    private CcdApi ccdApi;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

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
    void setUp() throws Exception {

        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
    }

    @Test
    public void should_create_case_from_valid_exception_record() throws Exception {
        // given
        CaseDetails exceptionRecord = createExceptionRecord("envelopes/exception-with-evidence.json");

        // when
        String caseCcdId = invokeCallbackEndpoint(exceptionRecord, "ccdCaseReference");

        // then
        await("Case is created")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> isCaseCreated(caseCcdId, exceptionRecord));

        verifyCaseIsCreated(caseCcdId, exceptionRecord);
    }

    /**
     * Hits the services callback endpoint with a request to create case upon an exception record.
     */
    private String invokeCallbackEndpoint(
        CaseDetails exceptionRecord,
        String searchCaseReferenceType
    ) throws IOException {
        Map<String, Object> exceptionRecordDataWithSearchFields = exceptionRecordDataWithSearchFields(
            exceptionRecord,
            searchCaseReferenceType
        );

        CaseDetails exceptionRecordWithSearchFields =
            exceptionRecord.toBuilder().data(exceptionRecordDataWithSearchFields).build();

        CallbackRequest callbackRequest = CallbackRequest
            .builder()
            .eventId("createCase")
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
            .post("/callback/create-case")
            .andReturn();

        return getCaseCcdId(response);
    }

    private String getCaseCcdId(Response response) throws IOException {
        assertThat(response.getStatusCode()).isEqualTo(200);

        final AboutToStartOrSubmitCallbackResponse callbackResponse =
            new ObjectMapper().readValue(response.getBody().asString(), AboutToStartOrSubmitCallbackResponse.class);
        return (String)callbackResponse.getData().get("CASE_REFERENCE");
    }

    private Map<String, Object> exceptionRecordDataWithSearchFields(
        CaseDetails exceptionRecord,
        String searchCaseReferenceType
    ) {
        Map<String, Object> exceptionRecordData = new HashMap<>(exceptionRecord.getData());
        exceptionRecordData.put("searchCaseReferenceType", searchCaseReferenceType);
        return exceptionRecordData;
    }

    private CaseDetails createExceptionRecord(String resourceName) throws Exception {
        UUID poBox = UUID.randomUUID();

        envelopeMessager.sendMessageFromFile(resourceName, "0000000000000000", null, poBox, dmUrl);

        await("Exception record is created")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> lookUpExceptionRecord(poBox).isPresent());

        return lookUpExceptionRecord(poBox).get();
    }

    private Optional<CaseDetails> lookUpExceptionRecord(UUID randomPoBox) {
        List<CaseDetails> caseDetailsList = caseSearcher.search(
            SampleData.JURSIDICTION,
            SampleData.JURSIDICTION + "_" + CreateExceptionRecord.CASE_TYPE,
            ImmutableMap.of(
                "case.poBox", randomPoBox.toString()
            )
        );
        return caseDetailsList.stream().findFirst();
    }

    private Boolean isCaseCreated(String caseCcdId, CaseDetails exceptionRecord) {

        CaseDetails createdCase = ccdApi.getCase(
            String.valueOf(caseCcdId),
            exceptionRecord.getJurisdiction()
        );

        return createdCase != null;
    }

    private void verifyCaseIsCreated(
        String caseCcdId,
        CaseDetails exceptionRecord
    ) {
        CaseDetails createdCase = ccdApi.getCase(
            String.valueOf(caseCcdId),
            exceptionRecord.getJurisdiction()
        );

        assertTrue(createdCase != null);
    }
}
