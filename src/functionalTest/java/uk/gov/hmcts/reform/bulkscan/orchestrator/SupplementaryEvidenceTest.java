package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.awaitility.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.dm.DocumentManagementUploadService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.document.domain.UploadResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("nosb") // no servicebus queue handler registration
public class SupplementaryEvidenceTest {

    @Autowired
    private CaseRetriever caseRetriever;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    private CaseDetails caseDetails;

    private String dmUrl;

    @Autowired
    private DocumentManagementUploadService dmUploadService;

    @Before
    public void setup() {
        String caseData = SampleData.fileContentAsString("envelopes/new-envelope.json");
        Envelope newEnvelope = EnvelopeParser.parse(caseData);

        caseDetails = ccdCaseCreator.createCase(newEnvelope);

        UploadResponse uploadResponse = dmUploadService.uploadToDmStore(
            "Certificate.pdf",
            "documents/supplementary-evidence.pdf"
        );
        List<String> scannedDocumentUrls = ScannedDocumentsHelper.getScannedDocumentUrls(uploadResponse);
        dmUrl = scannedDocumentUrls.isEmpty() ? null : scannedDocumentUrls.get(0);
    }

    @Test
    public void should_attach_supplementary_evidence_to_the_case() throws Exception {
        // when
        envelopeMessager.sendMessageFromFile(
            "envelopes/supplementary-evidence-envelope.json",
            String.valueOf(caseDetails.getId()),
            UUID.randomUUID(),
            dmUrl
        );

        // then
        assertThat(dmUrl).isNotNull();
        await("Supplementary evidence is attached to the case in ccd")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.TWO_SECONDS)
            .until(() -> hasCaseBeenUpdatedWithSupplementaryEvidence(caseDetails));

        verifySupplementaryEvidenceDetailsUpdated(caseDetails, "envelopes/supplementary-evidence-envelope.json");
    }

    private void verifySupplementaryEvidenceDetailsUpdated(CaseDetails caseDetails, String jsonFileName) {

        String caseData = SampleData.fileContentAsString(jsonFileName);
        Envelope updatedEnvelope = EnvelopeParser.parse(caseData);

        CaseDetails updatedCaseDetails = caseRetriever.retrieve(
            caseDetails.getJurisdiction(),
            SampleData.BULK_SCANNED_CASE_TYPE,
            String.valueOf(caseDetails.getId())
        );

        List<ScannedDocument> updatedScannedDocuments = ScannedDocumentsHelper.getScannedDocuments(updatedCaseDetails);

        ScannedDocument updatedDocument = updatedScannedDocuments.get(0);

        Document queueDocument = updatedEnvelope.documents.get(0);

        assertThat(updatedDocument.fileName).isEqualTo(queueDocument.fileName);
        assertThat(updatedDocument.controlNumber).isEqualTo(queueDocument.controlNumber);
        assertThat(updatedDocument.type).isEqualTo(queueDocument.type);
        assertThat(updatedDocument.url).isNotNull();
        assertThat(updatedDocument.url.documentUrl).isEqualTo(dmUrl);
    }

    private boolean hasCaseBeenUpdatedWithSupplementaryEvidence(CaseDetails caseDetails) {

        CaseDetails updatedCaseDetails = caseRetriever.retrieve(
            caseDetails.getJurisdiction(),
            SampleData.BULK_SCANNED_CASE_TYPE,
            String.valueOf(caseDetails.getId())
        );

        List<ScannedDocument> updatedScannedDocuments = ScannedDocumentsHelper.getScannedDocuments(updatedCaseDetails);
        return updatedScannedDocuments.size() > 0;
    }
}
