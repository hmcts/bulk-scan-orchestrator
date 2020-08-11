package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentType;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Service
public class TransformationRequestCreator {

    private final String documentManagementUrl;
    private final String documentManagementContextPath;

    public TransformationRequestCreator(
        @Value("${document_management.url}") final String documentManagementUrl,
        @Value("${document_management.context-path}") final String documentManagementContextPath
    ) {
        this.documentManagementUrl = documentManagementUrl;
        this.documentManagementContextPath = documentManagementContextPath;
    }

    public TransformationRequest create(ExceptionRecord exceptionRecord) {
        return new TransformationRequest(
            exceptionRecord.id,
            exceptionRecord.caseTypeId,
            exceptionRecord.envelopeId,
            false,
            exceptionRecord.poBox,
            exceptionRecord.poBoxJurisdiction,
            exceptionRecord.journeyClassification,
            exceptionRecord.formType,
            exceptionRecord.deliveryDate,
            exceptionRecord.openingDate,
            exceptionRecord.scannedDocuments,
            exceptionRecord.ocrDataFields
        );
    }

    public TransformationRequest create(Envelope envelope) {
        return new TransformationRequest(
            null,
            null,
            envelope.id,
            true,
            envelope.poBox,
            envelope.jurisdiction,
            envelope.classification,
            envelope.formType,
            toLocalDateTime(envelope.deliveryDate),
            toLocalDateTime(envelope.openingDate),
            mapDocuments(envelope.documents),
            mapOcrDataFields(envelope.ocrData)
        );
    }

    private List<OcrDataField> mapOcrDataFields(
        List<uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.OcrDataField>
            envelopeOcrDataFields
    ) {
        if (envelopeOcrDataFields != null) {
            return envelopeOcrDataFields
                .stream()
                .map(field -> new OcrDataField(field.name, field.value))
                .collect(toList());
        } else {
            return emptyList();
        }
    }

    private List<ScannedDocument> mapDocuments(List<Document> envelopeDocuments) {
        return envelopeDocuments
            .stream()
            .map(this::mapDocument)
            .collect(toList());
    }

    private ScannedDocument mapDocument(Document envelopeDocument) {
        return new ScannedDocument(
            DocumentType.valueOf(envelopeDocument.type.toUpperCase()),
            envelopeDocument.subtype,
            documentUrl(envelopeDocument),
            envelopeDocument.controlNumber,
            envelopeDocument.fileName,
            toLocalDateTime(envelopeDocument.scannedAt),
            toLocalDateTime(envelopeDocument.deliveryDate)
        );
    }

    private DocumentUrl documentUrl(Document envelopeDocument) {
        String documentUrl = String.join(
            "/",
            documentManagementUrl,
            documentManagementContextPath,
            envelopeDocument.uuid
        );

        String documentBinaryUrl = documentUrl + "/binary";

        return new DocumentUrl(
            documentUrl,
            documentBinaryUrl,
            envelopeDocument.fileName
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
