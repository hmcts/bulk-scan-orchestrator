package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.apache.commons.lang3.StringUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;

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

    public static List<CcdCollectionElement<ScannedDocument>> mapDocuments(List<Document> documents, String dmApiUrl) {
        return documents
            .stream()
            .map((Document document) -> mapDocument(document, dmApiUrl))
            .map(CcdCollectionElement::new)
            .collect(Collectors.toList());
    }

    public static ScannedDocument mapDocument(Document document, String dmApiUrl) {
        if (document == null) {
            return null;
        } else {
            return new ScannedDocument(
                document.fileName,
                document.controlNumber,
                document.type,
                document.subtype,
                getLocalDateTime(document.scannedAt),
                new CcdDocument(StringUtils.join(dmApiUrl, "/", document.uuid)),
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
