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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ModelMapper<T extends CaseData> {

    public abstract T mapEnvelope(Envelope envelope);

    List<CcdCollectionElement<ScannedDocument>> mapDocuments(List<Document> documents) {
        return documents
            .stream()
            .map(this::mapDocument)
            .map(CcdCollectionElement::new)
            .collect(Collectors.toList());
    }

    private ScannedDocument mapDocument(Document document) {
        return new ScannedDocument(
            document.fileName,
            document.controlNumber,
            document.type,
            getLocalDateTime(document.scannedAt),
            new CcdDocument(document.url),
            null
        );
    }

    LocalDateTime getLocalDateTime(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
