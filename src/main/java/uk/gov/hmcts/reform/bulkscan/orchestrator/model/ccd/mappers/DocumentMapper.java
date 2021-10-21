package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;

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
        List<DocumentHashProvider.DocumentAndHash> documentsAndHashes,
        String dmApiUrl,
        String contextPath,
        Instant deliveryDate
    ) {
        return documentsAndHashes
            .stream()
            .map(d -> mapDocument(d, dmApiUrl, contextPath, deliveryDate))
            .map(CcdCollectionElement::new)
            .collect(Collectors.toList());
    }

    public static LocalDateTime getLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }

    private static ScannedDocument mapDocument(
        DocumentHashProvider.DocumentAndHash documentAndHash,
        String dmApiUrl,
        String contextPath,
        Instant deliveryDate
    ) {
        if (documentAndHash == null || documentAndHash.document == null) {
            return null;
        } else {
            return new ScannedDocument(
                documentAndHash.document.fileName,
                documentAndHash.document.controlNumber,
                documentAndHash.document.type,
                documentAndHash.document.subtype,
                getLocalDateTime(documentAndHash.document.scannedAt),
                new CcdDocument(
                        String.join("/", dmApiUrl, contextPath, documentAndHash.document.uuid),
                        documentAndHash.hash
                ),
                getLocalDateTime(
                        documentAndHash.document.deliveryDate != null
                                ? documentAndHash.document.deliveryDate
                                : deliveryDate
                ),
                null
            );
        }
    }
}
