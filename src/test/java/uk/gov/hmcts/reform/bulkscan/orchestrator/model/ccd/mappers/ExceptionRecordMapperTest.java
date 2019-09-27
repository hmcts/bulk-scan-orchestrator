package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.OcrDataField;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;

class ExceptionRecordMapperTest {

    private final ExceptionRecordMapper mapper = exceptionRecordMapper("BULKSCAN");

    @Test
    public void mapEnvelope_maps_all_fields_correctly() {
        // given
        Envelope envelope = envelope(2);

        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.classification).isEqualTo(envelope.classification.name());
        assertThat(exceptionRecord.deliveryDate).isEqualTo(
            LocalDateTime.ofInstant(envelope.deliveryDate, ZoneId.systemDefault())
        );

        assertThat(exceptionRecord.jurisdiction).isEqualTo(envelope.jurisdiction);

        assertThat(exceptionRecord.openingDate).isEqualTo(
            LocalDateTime.ofInstant(envelope.openingDate, ZoneId.systemDefault())
        );

        assertThat(exceptionRecord.poBox).isEqualTo(envelope.poBox);
        assertThat(exceptionRecord.scannedDocuments.size()).isEqualTo(envelope.documents.size());

        assertThat(toEnvelopeDocuments(exceptionRecord.scannedDocuments))
            .usingFieldByFieldElementComparator()
            .containsAll(envelope.documents);

        assertThat(ocrDataAsList(exceptionRecord.ocrData))
            .usingFieldByFieldElementComparator()
            .containsAll(envelope.ocrData);

        assertThat(toEnvelopeOcrDataWarnings(exceptionRecord.ocrDataValidationWarnings))
            .usingFieldByFieldElementComparator()
            .containsExactlyElementsOf(envelope.ocrDataValidationWarnings);

        assertThat(exceptionRecord.envelopeId).isEqualTo(envelope.id);
    }

    @Test
    public void mapEnvelope_handles_null_ocr_data() {
        Envelope envelope = envelope(2, emptyList(), null, emptyList());
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);
        assertThat(exceptionRecord.ocrData).isNull();
    }

    @Test
    public void mapEnvelope_maps_subtype_values_in_documents() {
        // given
        Envelope envelope = envelope(2, null, emptyList(), emptyList());

        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.scannedDocuments.size()).isEqualTo(envelope.documents.size());

        List<String> expectedDocumentSubtypeValues =
            envelope.documents.stream().map(d -> d.subtype).collect(toList());

        List<String> actualDocumentSubtypeValues =
            exceptionRecord.scannedDocuments.stream().map(d -> d.value.subtype).collect(toList());

        assertThat(actualDocumentSubtypeValues).isEqualTo(expectedDocumentSubtypeValues);
    }

    @Test
    public void mapEnvelope_sets_warnings_presence_correctly() {
        Envelope envelopeWithWarning = envelope(2, null, emptyList(), newArrayList("Warning"));
        Envelope envelopeWithoutWarning = envelope(2, null, emptyList(), emptyList());

        assertThat(mapper.mapEnvelope(envelopeWithWarning).displayWarnings).isEqualTo("Yes");
        assertThat(mapper.mapEnvelope(envelopeWithoutWarning).displayWarnings).isEqualTo("No");
    }

    @Test
    public void mapEnvelope_copies_envelope_id_when_jurisdiction_supports_duplicate_prevention() {
        // given
        String supportedJurisdiction = "supported-jurisdiction1";
        ExceptionRecordMapper exceptionRecordMapper = exceptionRecordMapper(supportedJurisdiction, "jurisdiction2");

        // when
        Envelope envelope = envelopeWithJurisdiction(supportedJurisdiction);

        // then
        ExceptionRecord exceptionRecord = exceptionRecordMapper.mapEnvelope(envelope);

        assertThat(exceptionRecord.envelopeId).isEqualTo(envelope.id);
    }

    @Test
    public void mapEnvelope_sets_envelope_id_to_null_when_jurisdiction_does_not_support_duplicate_prevention() {
        // given
        ExceptionRecordMapper exceptionRecordMapper = exceptionRecordMapper("jurisdiction1", "jurisdiction2");

        // when
        Envelope envelope = envelopeWithJurisdiction("unsupported-jurisdiction1");

        // then
        ExceptionRecord exceptionRecord = exceptionRecordMapper.mapEnvelope(envelope);

        assertThat(exceptionRecord.envelopeId).isNull();
    }

    private Envelope envelopeWithJurisdiction(String jurisdiction) {
        return envelope(Classification.NEW_APPLICATION, jurisdiction, "1231231232312765");
    }

    private List<OcrDataField> ocrDataAsList(List<CcdCollectionElement<CcdKeyValue>> ocrData) {
        return ocrData
            .stream()
            .map(element -> new OcrDataField(element.value.key, element.value.value))
            .collect(Collectors.toList());
    }

    private List<String> toEnvelopeOcrDataWarnings(List<CcdCollectionElement<String>> ocrDataValidationWarnings) {
        return ocrDataValidationWarnings
            .stream()
            .map(e -> e.value)
            .collect(toList());
    }

    private List<Document> toEnvelopeDocuments(List<CcdCollectionElement<ScannedDocument>> ccdDocuments) {
        return ccdDocuments
            .stream()
            .map(e -> e.value)
            .map(scannedDocument ->
                new Document(
                    scannedDocument.fileName,
                    scannedDocument.controlNumber,
                    scannedDocument.type,
                    scannedDocument.subtype,
                    scannedDocument.scannedDate.atZone(ZoneId.systemDefault()).toInstant(),
                    StringUtils.substringAfterLast(scannedDocument.url.documentUrl, "/"),
                    scannedDocument.deliveryDate.atZone(ZoneId.systemDefault()).toInstant()
                )
            ).collect(toList());
    }

    private ExceptionRecordMapper exceptionRecordMapper(String... jurisdictionsWithDuplicatePrevention) {
        return new ExceptionRecordMapper(
            "https://example.gov.uk",
            "files",
            newArrayList(jurisdictionsWithDuplicatePrevention)
        );
    }
}
