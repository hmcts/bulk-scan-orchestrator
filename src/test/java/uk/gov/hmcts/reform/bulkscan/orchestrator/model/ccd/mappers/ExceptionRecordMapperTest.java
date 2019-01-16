package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;

public class ExceptionRecordMapperTest {

    private final ExceptionRecordMapper mapper = new ExceptionRecordMapper();

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

        assertThat(ocrDataAsMap(exceptionRecord.ocrData)).isEqualTo(envelope.ocrData);
    }

    @Test
    public void mapEnvelope_handles_null_ocr_data() {
        Envelope envelope = envelope(2, null);
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);
        assertThat(exceptionRecord.ocrData).isNull();
    }

    @Test
    public void mapEnvelope_maps_subtype_values_in_documents() {
        // given
        Envelope envelope = envelope(2, null);

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

    private Map<String, String> ocrDataAsMap(List<CcdCollectionElement<CcdKeyValue>> ocrData) {
        return ocrData
            .stream()
            .map(element -> element.value)
            .collect(
                toMap(kv -> kv.key, kv -> kv.value)
            );
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
                    scannedDocument.url.documentUrl
                )
            ).collect(toList());
    }
}
