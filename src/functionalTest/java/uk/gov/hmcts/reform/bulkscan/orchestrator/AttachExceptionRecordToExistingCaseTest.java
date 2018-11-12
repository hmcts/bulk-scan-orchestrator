package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

    private CaseDetails existingCase;

    private CaseDetails exceptionRecordCase;

    @BeforeEach
    public void setup() throws InterruptedException, ServiceBusException, JSONException {
        String caseData = SampleData.fileContentAsString("envelopes/new-envelope.json");
        Envelope newEnvelope = EnvelopeParser.parse(caseData);

        existingCase = ccdCaseCreator.createCase(newEnvelope);

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
    @Disabled
    public void should_atatch_exception_record_to_the_existing_case() {

        // when
        CallbackRequest callbackRequest = CallbackRequest
            .builder()
            .eventId("attach_case")
            .caseDetails(exceptionRecordCase)
            .caseDetailsBefore(existingCase)
            .build();

        Response callbackResponse = RestAssured
            .given()
            .baseUri(testUrl)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(SyntheticHeaders.SYNTHETIC_TEST_SOURCE, "Bulk Scan Orchestrator Functional test")
            .body(callbackRequest)
            .when()
            .post("/callback/{type}", "attach_case")
            .andReturn();

        //then
        assertThat(callbackResponse.getStatusCode()).isEqualTo(200);
        verifyExistingCaseIsUpdatedWithExceptionRecordCaseDetails();
    }

    private boolean isExceptionRecordCreated(UUID randomPoBox) {
        List<CaseDetails> caseDetailsList = caseSearcher.search(
            SampleData.JURSIDICTION,
            EnvelopeEventProcessor.EXCEPTION_RECORD_CASE_TYPE,
            ImmutableMap.of(
                "case.poBox", randomPoBox.toString()
            )
        );

        exceptionRecordCase = caseDetailsList.isEmpty() ? null : caseDetailsList.get(0);
        return exceptionRecordCase != null;
    }

    private void verifyExistingCaseIsUpdatedWithExceptionRecordCaseDetails() {
        CaseDetails updatedCase = caseRetriever.retrieve(
            existingCase.getJurisdiction(),
            String.valueOf(existingCase.getId())
        );

        List<ScannedDocument> updatedScannedDocuments = getScannedDocumentsForSupplementaryEvidence(updatedCase);

        List<ScannedDocument> scannedDocuments = getScannedDocumentsForExceptionRecord(exceptionRecordCase);

        assertThat(scannedDocuments).isNotEmpty();
        assertThat(scannedDocuments.size()).isEqualTo(1);

        assertThat(updatedScannedDocuments).isNotEmpty();
        assertThat(updatedScannedDocuments.size()).isEqualTo(1);

        ScannedDocument updatedCaseEvidence = updatedScannedDocuments.get(0);
        ScannedDocument caseEvidence = scannedDocuments.get(0);

        assertThat(updatedCaseEvidence.url).isEqualTo(caseEvidence.url);
        assertThat(updatedCaseEvidence.controlNumber).isEqualTo(caseEvidence.controlNumber);
        assertThat(updatedCaseEvidence.type).isEqualTo(caseEvidence.type);
        assertThat(updatedCaseEvidence.fileName).isEqualTo(caseEvidence.fileName);
        assertThat(updatedCaseEvidence.scannedDate).isEqualTo(caseEvidence.scannedDate);
    }
}
