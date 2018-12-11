package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseSearcher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.DelegatePublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getOcrData;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CaseDataExtractor.getScannedDocuments;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
public class AttachExceptionRecordToExistingCaseTest {

    @Value("${test-url}")
    private String testUrl;

    @Autowired
    private CaseRetriever caseRetriever;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    @Autowired
    private CaseSearcher caseSearcher;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    private String dmUrl;

    @BeforeEach
    public void setup() throws Exception {

        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
    }

    @Test
    public void should_attach_exception_record_to_the_existing_case_with_no_evidence() throws Exception {
        //given
        CaseDetails caseDetails = createCase("envelopes/new-envelope.json");
        CaseDetails exceptionRecord = createExceptionRecord("envelopes/supplementary-evidence-envelope.json");

        // when
        invokeCallbackEndpointForLinkingDocsToCase(caseDetails, exceptionRecord);

        //then
        await("Exception record is attached to the case")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> isExceptionRecordAttachedToTheCase(caseDetails, 1));

        verifyExistingCaseIsUpdatedWithExceptionRecordData(caseDetails, exceptionRecord, 1);
    }

    @Test
    public void should_attach_exception_record_to_the_existing_case_with_evidence_documents() throws Exception {
        //given
        Envelope newEnvelope = updateEnvelope("envelopes/new-envelope-with-evidence.json");
        CaseDetails caseDetails = ccdCaseCreator.createCase(newEnvelope);
        CaseDetails exceptionRecord = createExceptionRecord("envelopes/supplementary-evidence-envelope.json");

        // when
        invokeCallbackEndpointForLinkingDocsToCase(caseDetails, exceptionRecord);

        //then
        await("Exception record is attached to the case")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> isExceptionRecordAttachedToTheCase(caseDetails, 2));

        verifyExistingCaseIsUpdatedWithExceptionRecordData(caseDetails, exceptionRecord, 1);
    }

    private void invokeCallbackEndpointForLinkingDocsToCase(CaseDetails caseDetails, CaseDetails exceptionRecord) {
        Map<String, Object> caseData = exceptionRecord.getData();
        caseData.put("attachToCaseReference", String.valueOf(caseDetails.getId()));

        CallbackRequest callbackRequest = CallbackRequest
            .builder()
            .eventId("attachToExistingCase")
            .caseDetails(exceptionRecord)
            .build();

        RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(testUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator Functional test")
            .body(callbackRequest)
            .when()
            .post("/callback/attach_case");
    }

    private CaseDetails createExceptionRecord(String resourceName) throws Exception {
        UUID poBox = UUID.randomUUID();

        envelopeMessager.sendMessageFromFile(resourceName, "0000000000000000", poBox, dmUrl);

        await("Exception record is created")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> lookUpExceptionRecord(poBox).isPresent());

        return lookUpExceptionRecord(poBox).get();
    }

    private Optional<CaseDetails> lookUpExceptionRecord(UUID randomPoBox) {
        List<CaseDetails> caseDetailsList = caseSearcher.search(
            SampleData.JURSIDICTION,
            SampleData.JURSIDICTION + "_" + DelegatePublisher.EXCEPTION_RECORD_CASE_TYPE,
            ImmutableMap.of(
                "case.poBox", randomPoBox.toString()
            )
        );
        return caseDetailsList.stream().findFirst();
    }

    private Boolean isExceptionRecordAttachedToTheCase(CaseDetails caseDetails, int expectedScannedDocsSize) {

        CaseDetails updatedCase = caseRetriever.retrieve(
            caseDetails.getJurisdiction(),
            String.valueOf(caseDetails.getId())
        );

        List<ScannedDocument> updatedScannedDocuments = getScannedDocuments(updatedCase);

        return updatedScannedDocuments.size() == expectedScannedDocsSize;
    }

    private void verifyExistingCaseIsUpdatedWithExceptionRecordData(
        CaseDetails originalCase,
        CaseDetails exceptionRecord,
        int expectedExceptionRecordsSize
    ) {
        CaseDetails updatedCase = caseRetriever.retrieve(
            originalCase.getJurisdiction(),
            String.valueOf(originalCase.getId())
        );

        List<ScannedDocument> updatedScannedDocuments = getScannedDocuments(updatedCase);

        List<ScannedDocument> exceptionRecordDocuments = getScannedDocuments(exceptionRecord);

        assertThat(updatedScannedDocuments).isNotEmpty();
        assertThat(exceptionRecordDocuments).isNotEmpty();

        assertThat(updatedScannedDocuments).containsAll(exceptionRecordDocuments);

        isExceptionReferenceAttachedToTheScannedDocuments(
            updatedScannedDocuments,
            exceptionRecord.getId(),
            expectedExceptionRecordsSize
        );

        assertCorrectOcrDataAfterUpdate(originalCase, exceptionRecord, updatedCase);
    }

    private void assertCorrectOcrDataAfterUpdate(
        CaseDetails originalCase,
        CaseDetails exceptionRecord,
        CaseDetails updatedCase
    ) {
        Map<String, String> exceptionRecordOcrData = getOcrData(exceptionRecord);

        if (exceptionRecordOcrData.isEmpty()) {
            assertThat(getOcrData(updatedCase)).isEqualTo(getOcrData(originalCase));
        } else {
            assertThat(getOcrData(updatedCase)).isEqualTo(exceptionRecordOcrData);
        }
    }

    private void isExceptionReferenceAttachedToTheScannedDocuments(
        List<ScannedDocument> updatedScannedDocuments,
        Long id,
        int expectedDocumentsSize
    ) {
        List<ScannedDocument> scannedDocuments = updatedScannedDocuments
            .stream()
            .filter(document -> String.valueOf(id).equals(document.exceptionReference))
            .collect(Collectors.toList());

        assertThat(scannedDocuments.size()).isEqualTo(expectedDocumentsSize);
    }

    private CaseDetails createCase(String jsonFileName) {
        String caseData = SampleData.fileContentAsString(jsonFileName);
        Envelope newEnvelope = EnvelopeParser.parse(caseData);
        return ccdCaseCreator.createCase(newEnvelope);
    }

    private Envelope updateEnvelope(String envelope) throws JSONException {
        String updatedCase = SampleData.fileContentAsString(envelope);
        JSONObject updatedCaseData = new JSONObject(updatedCase);

        JSONArray documents = updatedCaseData.getJSONArray("documents");
        for (int i = 0; i < documents.length(); i++) {
            JSONObject document = (JSONObject) documents.get(0);
            document.put("url", dmUrl);
        }
        return EnvelopeParser.parse(updatedCaseData.toString());
    }
}
