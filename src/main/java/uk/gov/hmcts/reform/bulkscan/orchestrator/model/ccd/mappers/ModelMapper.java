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

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.helper.ScannedDocumentsHelper.mapOcrDataToCcdFormat;

public abstract class ModelMapper<T extends CaseData> {

    public abstract T mapEnvelope(Envelope envelope);

    List<CcdCollectionElement<ScannedDocument>> mapDocuments(List<Document> documents) {
        return documents
            .stream()
            .map(this::mapDocument)
            .map(CcdCollectionElement::new)
            .collect(toList());
    }

    private ScannedDocument mapDocument(Document document) {
        return new ScannedDocument(
            document.fileName,
            document.controlNumber,
            document.type,
            getLocalDateTime(document.scannedAt),
            new CcdDocument(document.url),
            null,
            mapOcrDataToCcdFormat(document.ocrData)
        );
    }

    LocalDateTime getLocalDateTime(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

}
