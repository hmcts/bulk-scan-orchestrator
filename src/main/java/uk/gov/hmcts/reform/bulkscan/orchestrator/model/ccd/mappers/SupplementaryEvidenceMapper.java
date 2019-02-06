package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentMapper.mapDocuments;

@Component
public class SupplementaryEvidenceMapper {

    public SupplementaryEvidenceMapper() {
        // empty mapper construct
    }

    public SupplementaryEvidence mapEnvelope(Envelope envelope) {
        return new SupplementaryEvidence(mapDocuments(envelope.documents));
    }
}
