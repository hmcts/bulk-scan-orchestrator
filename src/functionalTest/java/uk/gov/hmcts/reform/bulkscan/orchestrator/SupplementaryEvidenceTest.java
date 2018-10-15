package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.Message;
import org.awaitility.Duration;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.TestHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

public class SupplementaryEvidenceTest extends BaseTest {

    @Test
    public void should_attach_supplementary_evidence_to_the_case() throws Exception {
        // given
        String caseData = SampleData.fromFile("envelopes/new-envelope.json");
        Envelope envelope = EnvelopeParser.parse(caseData);
        CaseDetails aCase = ccdCaseCreator.createCasee(envelope);
        Assert.assertNotNull(aCase.getId());

        // when
        JSONObject updateData = new JSONObject(SampleData.fromFile("envelopes/update-envelope.json"));
        updateData.put("case_ref", aCase.getId());
        byte[] updatedEnvelopeData = updateData.toString().getBytes();

        Message message = new Message();
        message.setMessageId(UUID.randomUUID().toString());
        message.setBody(updatedEnvelopeData);
        writeClient.send(message);

        // then
        await("Supplementary evidence is attached to the case in ccd")
            .atMost(60, TimeUnit.SECONDS)
            .pollInterval(Duration.TWO_SECONDS)
            .until(() -> caseUpdatedWithSupplementaryEvidence(updatedEnvelopeData));

        verifySupplementaryEvidenceDetailsUpdated(updatedEnvelopeData);

    }

    public void verifySupplementaryEvidenceDetailsUpdated(byte[] envelopeData) {
        Envelope updatedEnvelope = EnvelopeParser.parse(envelopeData);

        CaseDetails updatedCaseDetails = caseRetriever.retrieve(updatedEnvelope.jurisdiction, String.valueOf(updatedEnvelope.caseRef));
        List<ScannedDocument> updatedScannedDocuments = TestHelper.getScannedDocuments(updatedCaseDetails);

        ScannedDocument updatedDocument = updatedScannedDocuments.get(0);

        Document queueDocument = updatedEnvelope.documents.get(0);

        assertEquals(updatedDocument.fileName, queueDocument.fileName);
        assertEquals(updatedDocument.controlNumber, queueDocument.controlNumber);
        assertEquals(updatedDocument.type, queueDocument.type);
    }

    private Boolean caseUpdatedWithSupplementaryEvidence(byte[] envelopeJson) {

        Envelope envelope = EnvelopeParser.parse(envelopeJson);

        CaseDetails updatedCaseDetails = caseRetriever.retrieve(envelope.jurisdiction, envelope.caseRef);

        List<ScannedDocument> updatedScannedDocuments = TestHelper.getScannedDocuments(updatedCaseDetails);
        return  updatedScannedDocuments.size() > 0;
    }
}

