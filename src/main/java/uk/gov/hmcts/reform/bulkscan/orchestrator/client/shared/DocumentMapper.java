package uk.gov.hmcts.reform.bulkscan.orchestrator.client.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class DocumentMapper {

    private final String documentManagementUrl;
    private final String documentManagementContextPath;

    public DocumentMapper(
        @Value("${document_management.url}") final String documentManagementUrl,
        @Value("${document_management.context-path}") final String documentManagementContextPath
    ) {
        this.documentManagementUrl = documentManagementUrl;
        this.documentManagementContextPath = documentManagementContextPath;
    }

    public ScannedDocument toScannedDoc(Document doc) {
        if (doc == null) {
            return null;
        } else {
            return new ScannedDocument(
                DocumentType.valueOf(doc.type.toUpperCase()),
                doc.subtype,
                documentUrl(doc),
                doc.controlNumber,
                doc.fileName,
                toLocalDateTime(doc.scannedAt),
                toLocalDateTime(doc.deliveryDate)
            );
        }
    }

    private DocumentUrl documentUrl(Document doc) {
        String documentUrl = String.join(
            "/",
            documentManagementUrl,
            documentManagementContextPath,
            doc.uuid
        );

        return new DocumentUrl(
            documentUrl,
            documentUrl + "/binary",
            doc.fileName
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        } else {
            return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
        }
    }
}
