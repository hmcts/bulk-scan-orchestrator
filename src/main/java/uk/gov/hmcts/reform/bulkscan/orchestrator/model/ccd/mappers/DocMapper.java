package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.util.Util.getLocalDateTime;

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
        List<Document> existingDocuments,
        List<Document> docsToAdd,
        Instant deliveryDate,
        String jurisdiction
    ) {
        var allDocs =
            Stream
                .concat(existingDocuments.stream(), docsToAdd.stream())
                .collect(toList());

        Map<String, String>  map = cdamApiClient.getDocumentHash(jurisdiction, docsToAdd);
        return allDocs
            .stream()
            .map(document -> mapDocument(
                document,
                deliveryDate,
                (document == null || document.uuid == null) ? null : map.get(document.uuid)))
            .map(CcdCollectionElement::new)
            .collect(Collectors.toList());

    }

    private ScannedDocument mapDocument(
        Document document,
        Instant deliveryDate,
        String hashToken
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
                    hashToken
                ),
                getLocalDateTime(document.deliveryDate != null ? document.deliveryDate : deliveryDate),
                null
            );
        }
    }
}
