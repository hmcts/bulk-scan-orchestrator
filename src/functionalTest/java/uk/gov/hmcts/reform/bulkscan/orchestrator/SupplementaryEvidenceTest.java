package uk.gov.hmcts.reform.bulkscan.orchestrator;

import org.awaitility.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseTypeId;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SupplementaryEvidenceTest {

    @Autowired
    private CaseRetriever caseRetriever;

    @Autowired
    private CcdCaseCreator ccdCaseCreator;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Test
    public void should_attach_supplementary_evidence_to_the_case() throws Exception {
        // given
        String caseData = SampleData.fromFile("envelopes/new-envelope.json");
        Envelope newEnvelope = EnvelopeParser.parse(caseData);

        CaseDetails caseDetails = ccdCaseCreator.createCase(newEnvelope);
        assertThat(caseDetails.getId()).isNotNull();

        // when
        envelopeMessager.sendMessageFromFile("envelopes/update-envelope.json", caseDetails.getId());

        // then
        await("Supplementary evidence is attached to the case in ccd")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.TWO_SECONDS)
            .until(() -> hasCaseBeenUpdatedWithSupplementaryEvidence(caseDetails));

        verifySupplementaryEvidenceDetailsUpdated(caseDetails, "envelopes/update-envelope.json");
    }

    public void verifySupplementaryEvidenceDetailsUpdated(CaseDetails caseDetails, String jsonFileName) {

        String caseData = SampleData.fromFile(jsonFileName);
        Envelope updatedEnvelope = EnvelopeParser.parse(caseData);

        CaseDetails updatedCaseDetails = caseRetriever.retrieve(
            caseDetails.getJurisdiction(),
            CaseTypeId.BULK_SCANNED,
            String.valueOf(caseDetails.getId())
        );

        List<ScannedDocument> updatedScannedDocuments = ScannedDocumentsHelper.getScannedDocuments(updatedCaseDetails);

        ScannedDocument updatedDocument = updatedScannedDocuments.get(0);

        Document queueDocument = updatedEnvelope.documents.get(0);

        assertThat(updatedDocument.fileName).isEqualTo(queueDocument.fileName);
        assertThat(updatedDocument.controlNumber).isEqualTo(queueDocument.controlNumber);
        assertThat(updatedDocument.type).isEqualTo(queueDocument.type);
    }

    private Boolean hasCaseBeenUpdatedWithSupplementaryEvidence(CaseDetails caseDetails) {

        CaseDetails updatedCaseDetails = caseRetriever.retrieve(
            caseDetails.getJurisdiction(),
            CaseTypeId.BULK_SCANNED,
            String.valueOf(caseDetails.getId())
        );

        List<ScannedDocument> updatedScannedDocuments = ScannedDocumentsHelper.getScannedDocuments(updatedCaseDetails);
        return updatedScannedDocuments.size() > 0;
    }
}
