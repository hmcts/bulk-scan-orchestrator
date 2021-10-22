package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.shared.DocumentMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request.TransformationRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
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

    private final DocumentMapper docMapper;

    public TransformationRequestCreator(DocumentMapper docMapper) {
        this.docMapper = docMapper;
    }

    public TransformationRequest create(ExceptionRecord exceptionRecord, boolean ignoreWarnings) {
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
            exceptionRecord.ocrDataFields,
            ignoreWarnings
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
            envelope.documents
                    .stream()
                    .map(doc -> docMapper.toScannedDoc(doc, envelope.jurisdiction))
                    .collect(toList()),
            mapOcrDataFields(envelope.ocrData),
            true
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

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        } else {
            return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
        }
    }
}
