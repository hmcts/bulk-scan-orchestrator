package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DocumentMapper {

    private DocumentMapper() {
        // util class
    }

    public static List<CcdCollectionElement<ScannedDocument>> mapDocuments(
        Map<Document, String> documentsAndHashes,
        String dmApiUrl,
        String contextPath,
        Instant deliveryDate
    ) {
        return documentsAndHashes.entrySet()
            .stream()
            .map(e -> mapDocument(
                    e.getKey(),
                    e.getValue().equals("") ? null : e.getValue(),
                    dmApiUrl,
                    contextPath,
                    deliveryDate
            ))
            .map(CcdCollectionElement::new)
            .collect(Collectors.toList());
    }

    public static ScannedDocument mapDocument(
        Document document,
        String documentHash,
        String dmApiUrl,
        String contextPath,
        Instant deliveryDate
    ) {
        if (document == null) {
            return null;
        } else {
            return new ScannedDocument(
                document.fileName,
                document.controlNumber,
                document.type,
                document.subtype,
                getLocalDateTime(document.scannedAt),
                new CcdDocument(String.join("/", dmApiUrl, contextPath, document.uuid), documentHash),
                getLocalDateTime(document.deliveryDate != null ? document.deliveryDate : deliveryDate),
                null
            );
        }
    }

    public static LocalDateTime getLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
