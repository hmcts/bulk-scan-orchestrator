package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.ZoneId;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class SupplementaryEvidenceMapper {

    private SupplementaryEvidenceMapper() {
        // utility class
    }

    public static SupplementaryEvidence fromEnvelope(Envelope envelope) {
        List<CcdCollectionElement<ScannedDocument>> scannedDocuments =
            envelope
                .documents
                .stream()
                .map(document -> new CcdCollectionElement<>(fromEnvelopeDocument(document)))
                .collect(toList());

        return new SupplementaryEvidence(scannedDocuments);
    }

    private static ScannedDocument fromEnvelopeDocument(Document document) {
        return new ScannedDocument(
            document.fileName,
            document.controlNumber,
            document.type,
            document.scannedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
            new CcdDocument(document.url)
        );
    }
}
