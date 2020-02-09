package uk.gov.hmcts.reform.bulkscan.orchestrator;

import io.restassured.RestAssured;
import io.restassured.response.Response;
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
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.Instant.now;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getScannedDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ATTACH_TO_CASE_REFERENCE;

@SpringBootTest
@ActiveProfiles("nosb")
class AttachExceptionRecordWithOcrToExistingCaseTest {

    @Value("${test-url}")
    String testUrl;

    @Value("${document_management.url}")
    String documentManagementUrl;

    @Value("${document_management.context-path}")
    String dmContextPath;

    @Autowired CcdApi ccdApi;
    @Autowired CcdCaseCreator ccdCaseCreator;
    @Autowired CaseSearcher caseSearcher;
    @Autowired EnvelopeMessager envelopeMessager;
    @Autowired DocumentManagementUploadService dmUploadService;
    @Autowired CcdAuthenticatorFactory ccdAuthenticatorFactory;

    @Test
    @SuppressWarnings("unchecked")
    void should_update_case_with_ocr() throws Exception {
        //given
        CaseDetails existingCase = ccdCaseCreator.createCase(emptyList(), now());
        String caseId = String.valueOf(existingCase.getId());

        CaseDetails exceptionRecord = createExceptionRecord("envelopes/supplementary-evidence-with-ocr-envelope.json");
        String ocrCountry = "sample_country"; // country from OCR data in exception record json loaded above

        // when
        sendAttachRequest(exceptionRecord, caseId);

        // then
        CaseDetails updatedCase = ccdApi.getCase(caseId, existingCase.getJurisdiction());

        Map<String, String> address = (Map<String, String>) updatedCase.getData().get("address");
        assertThat(address.get("country")).isEqualTo(ocrCountry);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returns_payments_error_when_updating_case_with_ocr() throws Exception {
        //given
        CaseDetails existingCase = ccdCaseCreator.createCase(emptyList(), now()); // with no scanned documents
        String caseId = String.valueOf(existingCase.getId());

        CaseDetails exceptionRecord = createExceptionRecord(
            "envelopes/supplementary-evidence-with-ocr-with-payments-envelope.json"
        );
        String ocrCountry = "sample_country"; // country from OCR data in exception record json loaded above

        // when
        Response response = invokeAttachWithOcrEndpoint(exceptionRecord, caseId);

        // then
        assertThat(response.jsonPath().getList("errors")).isNotEmpty();
        assertThat(response.jsonPath().getList("errors"))
            .contains("The 'attach to case' event is not supported for the Exception Record with pending payments");

        // verify case is not updated
        CaseDetails updatedCase = ccdApi.getCase(caseId, existingCase.getJurisdiction());
        assertThat(getScannedDocuments(updatedCase)).isEmpty(); // no scanned documents
    }

    private CaseDetails createExceptionRecord(String resourceName) throws Exception {
        UUID poBox = UUID.randomUUID();

        String dmUrl = dmUploadService.uploadToDmStore("doc.pdf", "documents/supplementary-evidence.pdf");

        envelopeMessager.sendMessageFromFile(resourceName, "0000000000000000", null, poBox, dmUrl);

        await("Exception record is created")
            .atMost(30, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> caseSearcher.findExceptionRecord(poBox.toString()).isPresent());

        return caseSearcher.findExceptionRecord(poBox.toString()).get();
    }

    private void sendAttachRequest(CaseDetails exceptionRecord, String targetCaseId) {
        invokeAttachWithOcrEndpoint(exceptionRecord, targetCaseId)
            .then()
            .assertThat()
            .statusCode(200)
            .body("errors", empty());
    }

    private Response invokeAttachWithOcrEndpoint(CaseDetails exceptionRecord, String targetCaseId) {
        Map<String, Object> data = new HashMap<>(exceptionRecord.getData());
        data.put(ATTACH_TO_CASE_REFERENCE, targetCaseId);

        CallbackRequest request = CallbackRequest
            .builder()
            .eventId("attachToExistingCase")
            .caseDetails(exceptionRecord.toBuilder().data(data).build())
            .build();

        CcdAuthenticator ccdAuthenticator = ccdAuthenticatorFactory.createForJurisdiction(SampleData.JURSIDICTION);

        return RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(testUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator Functional test")
            .header(HttpHeaders.AUTHORIZATION, ccdAuthenticator.getUserToken())
            .header(CcdCallbackController.USER_ID, ccdAuthenticator.getUserDetails().getId())
            .body(request)
            .when()
            .post("/callback/attach_case");
    }
}
