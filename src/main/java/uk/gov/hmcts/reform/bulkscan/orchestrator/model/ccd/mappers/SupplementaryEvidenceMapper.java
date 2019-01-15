package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Component
public class SupplementaryEvidenceMapper implements ModelMapper<SupplementaryEvidence> {

    public SupplementaryEvidenceMapper() {
        // empty mapper construct
    }

    @Override
    public SupplementaryEvidence mapEnvelope(Envelope envelope) {
        return new SupplementaryEvidence(mapDocuments(envelope.documents));
    }

    @Override
    public ScannedDocument mapDocument(Document document) {
        return new ScannedDocument(
            document.fileName,
            document.controlNumber,
            isNotEmpty(document.subtype) ? document.subtype : document.type,
            null,
            getLocalDateTime(document.scannedAt),
            new CcdDocument(document.url),
            null
        );
    }
}
