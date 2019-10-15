package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
class CreateCaseTest {

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
    public void should_create_case_from_valid_exception_record() throws Exception {
        // given
        CaseDetails exceptionRecord = createExceptionRecord("envelopes/new-envelope-create-case-with-evidence.json");

        // when
        String caseCcdId = invokeCallbackEndpoint(exceptionRecord);

        // then
        CaseDetails createdCase = ccdApi.getCase(caseCcdId, exceptionRecord.getJurisdiction());
        assertThat(createdCase.getCaseTypeId()).isEqualTo(BULK_SCANNED_CASE_TYPE);
        assertThat(createdCase.getData().get("firstName")).isEqualTo("value1");
        assertThat(createdCase.getData().get("lastName")).isEqualTo("value2");
        assertThat(createdCase.getData().get("email")).isEqualTo("hello@test.com");
    }

    /**
     * Hits the services callback endpoint with a request to create case upon an exception record.
     */
    private String invokeCallbackEndpoint(
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

        return getCaseCcdId(response);
    }

    private String getCaseCcdId(Response response) throws IOException {
        assertThat(response.getStatusCode()).isEqualTo(200);

        final AboutToStartOrSubmitCallbackResponse callbackResponse =
            new ObjectMapper().readValue(response.getBody().asString(), AboutToStartOrSubmitCallbackResponse.class);
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
