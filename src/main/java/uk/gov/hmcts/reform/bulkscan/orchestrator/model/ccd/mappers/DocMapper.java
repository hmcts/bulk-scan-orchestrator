package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.stereotype.Component;
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

@Component
public class DocMapper {

    public List<CcdCollectionElement<ScannedDocument>> mapDocuments(
        List<Document> documents,
        String dmApiUrl,
        String contextPath,
        Instant deliveryDate
    ) {
        return documents
            .stream()
            .map(document -> mapDocument(document, dmApiUrl, contextPath, deliveryDate))
            .map(CcdCollectionElement::new)
            .collect(Collectors.toList());
    }

    public LocalDateTime getLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }

    private ScannedDocument mapDocument(
        Document document,
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
                new CcdDocument(String.join("/", dmApiUrl, contextPath, document.uuid)),
                getLocalDateTime(document.deliveryDate != null ? document.deliveryDate : deliveryDate),
                null
            );
        }
    }
}
