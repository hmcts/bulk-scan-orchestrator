package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

public final class DocumentsMapper {

    private DocumentsMapper() {
        // util class
    }

    public static List<CcdCollectionElement<ScannedDocument>> mapDocuments(List<Document> documents) {
        return documents
            .stream()
            .map(DocumentsMapper::mapDocument)
            .map(CcdCollectionElement::new)
            .collect(toList());
    }

    private static ScannedDocument mapDocument(Document document) {
        return new ScannedDocument(
            document.fileName,
            document.controlNumber,
            document.type,
            document.subtype,
            getLocalDateTime(document.scannedAt),
            new CcdDocument(document.url),
            null
        );
    }

    public static LocalDateTime getLocalDateTime(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
