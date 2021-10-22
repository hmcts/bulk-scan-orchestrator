package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
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

    private final String documentManagementUrl;
    private final String documentManagementContextPath;
    private final CdamApiClient cdamApiClient;

    public DocMapper(
            @Value("${document_management.url}") final String documentManagementUrl,
            @Value("${document_management.context-path}") final String documentManagementContextPath,
            CdamApiClient cdamApiClient
    ) {
        this.documentManagementUrl = documentManagementUrl;
        this.documentManagementContextPath = documentManagementContextPath;
        this.cdamApiClient = cdamApiClient;
    }
    
    public List<CcdCollectionElement<ScannedDocument>> mapDocuments(
        List<Document> documents,
        Instant deliveryDate
    ) {
        return documents
            .stream()
            .map(document -> mapDocument(document, deliveryDate, null))
            .map(CcdCollectionElement::new)
            .collect(Collectors.toList());
    }

    public List<CcdCollectionElement<ScannedDocument>> mapNewDocuments(
        List<Document> documents,
        Instant deliveryDate,
        String jurisdiction
    ) {
        return documents
            .stream()
            .map(document ->
                mapDocument(
                    document,
                    deliveryDate,
                    cdamApiClient.getDocumentHash(jurisdiction, document)
                )
            )
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
        Instant deliveryDate,
        String documentHash
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
                new CcdDocument(
                    String.join("/", documentManagementUrl, documentManagementContextPath, document.uuid),
                    documentHash
                ),
                getLocalDateTime(document.deliveryDate != null ? document.deliveryDate : deliveryDate),
                null
            );
        }
    }
}
