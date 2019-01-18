package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.DocumentsMapper.mapDocuments;

public class SupplementaryEvidenceMapper {

    private SupplementaryEvidenceMapper() {
        // util class
    }

    public static SupplementaryEvidence mapEnvelope(Envelope envelope) {
        return new SupplementaryEvidence(mapDocuments(envelope.documents));
    }
}
