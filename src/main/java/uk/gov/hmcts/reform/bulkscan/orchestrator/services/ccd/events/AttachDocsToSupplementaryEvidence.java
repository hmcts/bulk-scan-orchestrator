package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

@Component("attach-docs-to-supplementary-evidence")
class AttachDocsToSupplementaryEvidence extends AbstractStrategy {

    @Override
    Object mapEnvelopeToCaseDataObject(Envelope envelope) {
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

    AttachDocsToSupplementaryEvidence() {
        // empty strategy constructor
    }
}
