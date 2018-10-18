package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

@Component
class AttachDocsToSupplementaryEvidence extends AbstractEventPublisher {

    AttachDocsToSupplementaryEvidence() {
        // empty constructor for ccd event publisher
    }

    @Override
    CaseData mapEnvelopeToCaseDataObject(Envelope envelope) {
        return SupplementaryEvidenceMapper.fromEnvelope(envelope);
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
