package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ModelMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseTypeId;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

@Component
class AttachDocsToSupplementaryEvidence extends AbstractEventPublisher {

    private final ModelMapper<? extends CaseData> mapper;

    AttachDocsToSupplementaryEvidence(SupplementaryEvidenceMapper mapper) {
        super(CaseTypeId.BULK_SCANNED);
        this.mapper = mapper;
    }

    @Override
    CaseData mapEnvelopeToCaseDataObject(Envelope envelope) {
        return mapper.mapEnvelope(envelope);
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
