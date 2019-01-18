package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.List;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.getDocuments;

@Component
class AttachDocsToSupplementaryEvidence extends AbstractEventPublisher {

    @Override
    CaseData mapEnvelopeToCaseDataObject(Envelope envelope) {
        return SupplementaryEvidenceMapper.mapEnvelope(envelope);
    }

    @Override
    public CaseDataContent buildCaseDataContent(StartEventResponse eventResponse, Envelope envelope) {
        List<Document> documents = getDocuments(eventResponse.getCaseDetails());
        envelope.addDocuments(documents);

        return super.buildCaseDataContent(eventResponse, envelope);
    }

    @Override
    String getEventTypeId() {
        return "attachScannedDocs";
    }

    @Override
    String getEventSummary() {
        return "Attach scanned documents";
    }
}
