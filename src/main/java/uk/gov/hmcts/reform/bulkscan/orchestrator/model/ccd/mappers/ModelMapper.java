package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ModelMapper<T extends CaseData> {

    public abstract T fromEnvelope(Envelope envelope);

    List<CcdCollectionElement<ScannedDocument>> mapDocuments(List<Document> documents) {
        return documents
            .stream()
            .map(this::fromEnvelopeDocument)
            .map(CcdCollectionElement::new)
            .collect(Collectors.toList());
    }

    // private methods from Java9 only
    private ScannedDocument fromEnvelopeDocument(Document document) {
        return new ScannedDocument(
            document.fileName,
            document.controlNumber,
            document.type,
            getLocalDateTime(document.scannedAt).toLocalDate(),
            new CcdDocument(document.url)
        );
    }

    LocalDateTime getLocalDateTime(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
