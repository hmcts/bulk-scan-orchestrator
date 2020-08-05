package uk.gov.hmcts.reform.bulkscan.orchestrator;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.commons.lang3.StringUtils;
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
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.Instant.now;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getScannedDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE;

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
        List<ScannedDocument> scannedDocuments = getScannedDocuments(updatedCase);
        assertThat(scannedDocuments)
            .as("Scanned document were updated with documents from Exception Record")
            .extracting(doc -> tuple(doc.fileName, doc.controlNumber, doc.exceptionReference))
            .containsExactly(
                tuple("certificate1.pdf", "1545657689", Long.toString(exceptionRecord.getId()))
            ); // from exception record json loaded above
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_update_case_with_existing_documents_with_ocr_and_exception_record_documents() throws Exception {
        //given
        CaseDetails existingCase = createCaseWithDocument();
        String caseId = String.valueOf(existingCase.getId());

        CaseDetails exceptionRecord = createExceptionRecord("envelopes/supplementary-evidence-with-ocr-envelope.json");
        String ocrCountry = "sample_country"; // country from OCR data in exception record json loaded above

        // when
        sendAttachRequest(exceptionRecord, caseId);

        // then
        CaseDetails updatedCase = ccdApi.getCase(caseId, existingCase.getJurisdiction());

        Map<String, String> address = (Map<String, String>) updatedCase.getData().get("address");
        assertThat(address.get("country")).isEqualTo(ocrCountry);
        List<ScannedDocument> scannedDocuments = getScannedDocuments(updatedCase);
        assertThat(scannedDocuments)
            .as("Scanned document were updated with documents from Exception Record")
            .extracting(doc -> tuple(doc.fileName, doc.controlNumber, doc.exceptionReference))
            .containsExactlyInAnyOrder(
                tuple("evidence.pdf", "142525627", null),
                tuple("certificate1.pdf", "1545657689", Long.toString(exceptionRecord.getId()))
            );
    }

    @Test
    void should_not_attach_exception_record_with_pending_payments_when_classification_is_not_allowed()
        throws Exception {
        //given
        CaseDetails existingCase = ccdCaseCreator.createCase(emptyList(), now()); // with no scanned documents
        String caseId = String.valueOf(existingCase.getId());

        CaseDetails exceptionRecord = createExceptionRecord(
            "envelopes/supplementary-evidence-with-ocr-with-payments-envelope.json"
        );

        // when
        Response response = invokeAttachWithOcrEndpoint(exceptionRecord, caseId);

        // then
        assertThat(response.jsonPath().getList("errors"))
            .isNotEmpty()
            .contains("Cannot attach this exception record to a case because it has pending payments");

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
        data.put(SEARCH_CASE_REFERENCE, targetCaseId);

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
            .post("/callback/attach_case?ignore-warning=true");
    }

    private CaseDetails createCaseWithDocument() {
        String dmUrlOriginal = dmUploadService.uploadToDmStore("original.pdf", "documents/supplementary-evidence.pdf");
        String documentUuid = StringUtils.substringAfterLast(dmUrlOriginal, "/");

        return ccdCaseCreator.createCase(
            singletonList(
                new Document("evidence.pdf", "142525627", "other", null, Instant.now(), documentUuid, Instant.now())
            ),
            Instant.now()
        );
    }
}
