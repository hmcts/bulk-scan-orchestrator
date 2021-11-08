package uk.gov.hmcts.reform.bulkscan.orchestrator.client.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.cdam.CdamApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.util.Util.getLocalDateTime;

@SuppressWarnings("checkstyle:LineLength")
@Component
public class DocumentMapper {

    private final String documentManagementUrl;
    private final String documentManagementContextPath;
    private final CdamApiClient cdamApiClient;

    public DocumentMapper(
            @Value("${document_management.url}") final String documentManagementUrl,
            @Value("${document_management.context-path}") final String documentManagementContextPath,
            CdamApiClient cdamApiClient
    ) {
        this.documentManagementUrl = documentManagementUrl;
        this.documentManagementContextPath = documentManagementContextPath;
        this.cdamApiClient = cdamApiClient;
    }

    public uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument toScannedDoc(Document doc) {
        if (doc == null) {
            return null;
        } else {
            return new ScannedDocument(
                DocumentType.valueOf(doc.type.toUpperCase()),
                doc.subtype,
                documentUrl(doc),
                doc.controlNumber,
                doc.fileName,
                getLocalDateTime(doc.scannedAt),
                getLocalDateTime(doc.deliveryDate)
            );
        }
    }

    public List<CcdCollectionElement<uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument>> mapToCcdScannedDocuments(
        List<Document> existingDocuments,
        List<Document> docsToAdd,
        Instant deliveryDate,
        String jurisdiction
    ) {
        var allDocs =
                Stream
                        .concat(existingDocuments.stream(), docsToAdd.stream())
                        .collect(toList());

        Map<String, String> map = cdamApiClient.getDocumentHash(jurisdiction, docsToAdd);
        return allDocs
                .stream()
                .map(document -> mapToCcdScannedDocument(
                    document,
                    deliveryDate,
                    (document == null || document.uuid == null) ? null : map.get(document.uuid)
                ))
                .map(CcdCollectionElement::new)
                .collect(Collectors.toList());

    }

    private uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument mapToCcdScannedDocument(
        Document document,
        Instant deliveryDate,
        String hashToken
    ) {
        if (document == null) {
            return null;
        } else {
            return new uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument(
                document.fileName,
                document.controlNumber,
                document.type,
                document.subtype,
                getLocalDateTime(document.scannedAt),
                new CcdDocument(
                    getDocumentUrl(document),
                    hashToken
                ),
                getLocalDateTime(document.deliveryDate != null ? document.deliveryDate : deliveryDate),
                null
            );
        }
    }

    private DocumentUrl documentUrl(Document doc) {
        String documentUrl = getDocumentUrl(doc);

        return new DocumentUrl(
            documentUrl,
            documentUrl + "/binary",
            doc.fileName
        );
    }

    private String getDocumentUrl(Document doc) {
        return String.join(
            "/",
            documentManagementUrl,
            documentManagementContextPath,
            doc.uuid
        );
    }
}
