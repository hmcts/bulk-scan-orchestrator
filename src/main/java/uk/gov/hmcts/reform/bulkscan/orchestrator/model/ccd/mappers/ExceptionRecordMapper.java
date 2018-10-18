package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static java.util.stream.Collectors.toList;

public final class ExceptionRecordMapper {

    private ExceptionRecordMapper() {
        // utility class
    }

    public static ExceptionRecord fromEnvelope(Envelope envelope) {
        List<CcdCollectionElement<ScannedDocument>> scannedDocuments =
            envelope
                .documents
                .stream()
                .map(document -> new CcdCollectionElement<>(fromEnvelopeDocument(document)))
                .collect(toList());

        return new ExceptionRecord(
            envelope.classification.name(),
            envelope.poBox,
            envelope.jurisdiction,
            getLocalDateTime(envelope.deliveryDate),
            getLocalDateTime(envelope.openingDate),
            scannedDocuments
        );
    }

    private static ScannedDocument fromEnvelopeDocument(Document document) {
        return new ScannedDocument(
            document.fileName,
            document.controlNumber,
            document.type,
            document.scannedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
            new CcdDocument(document.url)
        );
    }

    private static LocalDateTime getLocalDateTime(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
