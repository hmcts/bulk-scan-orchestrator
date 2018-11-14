package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.EnvelopeEventProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CallbackRequest;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.logging.appinsights.SyntheticHeaders;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.getScannedDocumentsForExceptionRecord;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.getScannedDocumentsForSupplementaryEvidence;

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
    private CallbackProcessor callbackProcessor;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    private String dmUrl;

    private CaseDetails caseDetails;

    private CaseDetails exceptionRecord;

    @BeforeEach
    public void setup() throws InterruptedException, ServiceBusException, JSONException {

        dmUrl = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );

        UUID randomPoBox = UUID.randomUUID();

        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            "0000000000000000",
            randomPoBox,
            dmUrl
        );

        await("Exception record is created")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> isExceptionRecordCreated(randomPoBox));
    }

    @Test
    public void should_atatch_exception_record_to_the_existing_case() {
        //given
        String caseData = SampleData.fileContentAsString("envelopes/new-envelope.json");
        Envelope newEnvelope = EnvelopeParser.parse(caseData);
        caseDetails = ccdCaseCreator.createCase(newEnvelope);

        // when
        callCallbackUrlToAttachExceptionToCase(caseDetails, exceptionRecord);

        //then
        await("Exception record is attached to the case")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> isExceptionRecordAttachedToTheCase(1));
    }

    @Test
    public void should_atatch_exception_record_to_the_existing_case_with_evidence_documents() throws JSONException {
        //given
        Envelope newEnvelope = updateEnvelope("envelopes/new-envelope-with-evidence.json");
        caseDetails = ccdCaseCreator.createCase(newEnvelope);

        // when
        callCallbackUrlToAttachExceptionToCase(caseDetails, exceptionRecord);

        //then
        await("Exception record is attached to the case")
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .until(() -> isExceptionRecordAttachedToTheCase(2));
    }

    private void callCallbackUrlToAttachExceptionToCase(CaseDetails caseDetails, CaseDetails exceptionRecord) {
        Map<String, Object> caseData = exceptionRecord.getData();
        caseData.put("attachToCaseReference", String.valueOf(caseDetails.getId()));

        CallbackRequest callbackRequest = CallbackRequest
            .builder()
            .eventId("attachToExistingCase")
            .caseDetails(exceptionRecord)
            .build();

        RestAssured
            .given()
            .baseUri(testUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator Functional test")
            .body(callbackRequest)
            .when()
            .post("/callback/attach_case");
    }

    private boolean isExceptionRecordCreated(UUID randomPoBox) {
        List<CaseDetails> caseDetailsList = caseSearcher.search(
            SampleData.JURSIDICTION,
            EnvelopeEventProcessor.EXCEPTION_RECORD_CASE_TYPE,
            ImmutableMap.of(
                "case.poBox", randomPoBox.toString()
            )
        );

        exceptionRecord = caseDetailsList.isEmpty() ? null : caseDetailsList.get(0);
        return exceptionRecord != null;
    }

    private Boolean isExceptionRecordAttachedToTheCase(int expectedScannedDocsSize) {

        CaseDetails updatedCase = caseRetriever.retrieve(
            caseDetails.getJurisdiction(),
            String.valueOf(caseDetails.getId())
        );

        List<ScannedDocument> updatedScannedDocuments = getScannedDocumentsForSupplementaryEvidence(updatedCase);
        List<ScannedDocument> scannedDocuments = getScannedDocumentsForExceptionRecord(exceptionRecord);

        assertThat(scannedDocuments).isNotEmpty();
        assertThat(updatedScannedDocuments).isNotEmpty();

        return updatedScannedDocuments.size() == expectedScannedDocsSize
            && updatedScannedDocuments.containsAll(scannedDocuments);
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
