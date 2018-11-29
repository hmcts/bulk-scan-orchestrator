package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

@Component
public class SupplementaryEvidenceMapper extends ModelMapper<SupplementaryEvidence> {

    public SupplementaryEvidenceMapper() {
        // empty mapper construct
    }

    @Override
    public SupplementaryEvidence mapEnvelope(Envelope envelope) {
        return new SupplementaryEvidence(mapDocuments(envelope.getDocuments()));
    }
}
