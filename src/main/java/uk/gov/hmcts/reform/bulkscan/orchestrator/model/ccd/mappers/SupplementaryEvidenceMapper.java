package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

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
    ScannedDocument mapDocument(Document document) {
        if (StringUtils.isNotEmpty(document.subtype)) {
            return new ScannedDocument(
                document.fileName,
                document.controlNumber,
                document.subtype,
                null,
                getLocalDateTime(document.scannedAt),
                new CcdDocument(document.url),
                null
            );
        } else {
            return new ScannedDocument(
                document.fileName,
                document.controlNumber,
                document.type,
                null,
                getLocalDateTime(document.scannedAt),
                new CcdDocument(document.url),
                null
            );
        }
    }
}
