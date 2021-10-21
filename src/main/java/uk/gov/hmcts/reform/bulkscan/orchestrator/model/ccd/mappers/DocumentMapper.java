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
import java.util.stream.Collectors;

public class DocumentMapper {

    private DocumentMapper() {
        // util class
    }

    public static List<CcdCollectionElement<ScannedDocument>> mapDocuments(
        List<DocumentAndHash> documentsAndHashes,
        String dmApiUrl,
        String contextPath,
        Instant deliveryDate
    ) {
        return documentsAndHashes
            .stream()
            .map(d -> mapDocument(
                    d.document,
                    d.hash.equals("") ? null : d.hash,
                    dmApiUrl,
                    contextPath,
                    deliveryDate
            ))
            .map(CcdCollectionElement::new)
            .collect(Collectors.toList());
    }

    public static LocalDateTime getLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }

    private static ScannedDocument mapDocument(
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

    public static class DocumentAndHash {
        final Document document;
        final String hash;

        public DocumentAndHash(Document document, String hash) {
            this.document = document;
            this.hash = hash;
        }
    }
}
