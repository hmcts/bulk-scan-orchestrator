package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class SupplementaryEvidenceMapperTest {

    private static final SupplementaryEvidenceMapper mapper = new SupplementaryEvidenceMapper();

    @Test
    public void from_envelope_maps_all_fields_correctly() {
        // given
        Envelope envelope = SampleData.envelope(1);

        // when
        SupplementaryEvidence result = mapper.mapEnvelope(envelope);

        // then
        assertThat(result.evidenceHandled).isEqualTo("No");
        assertThat(result.scannedDocuments.size()).isEqualTo(1);

        Document envelopeDoc = envelope.documents.get(0);
        ScannedDocument resultDoc = result.scannedDocuments.get(0).value;

        assertThat(resultDoc.controlNumber).isEqualTo(envelopeDoc.controlNumber);
        assertThat(resultDoc.fileName).isEqualTo(envelopeDoc.fileName);
        assertThat(resultDoc.type).isEqualTo(envelopeDoc.type);
        assertThat(resultDoc.url.documentUrl).isEqualTo(envelopeDoc.url);
        assertThat(resultDoc.scannedDate).isEqualTo(toLocalDateTime(envelopeDoc.scannedAt));
    }

    @Test
    public void from_envelope_returns_supplementary_evidence_with_all_documents() {
        // given
        int numberOfDocuments = 12;
        Envelope envelope = SampleData.envelope(12);

        // when
        SupplementaryEvidence result = mapper.mapEnvelope(envelope);

        // then
        assertThat(result.scannedDocuments.size()).isEqualTo(numberOfDocuments);
        assertThat(result.scannedDocuments)
            .extracting(doc -> doc.value.fileName)
            .containsExactlyElementsOf(envelope.documents.stream().map(d -> d.fileName).collect(toList()));
    }

    @Test
    public void from_envelope_returns_supplementary_evidence_with_subtype_value_in_documents() {
        // given
        int numberOfDocuments = 3;
        Envelope envelope = SampleData.envelope(3);

        // when
        SupplementaryEvidence result = mapper.mapEnvelope(envelope);

        // then
        assertThat(result.scannedDocuments.size()).isEqualTo(numberOfDocuments);
        assertThat(result.scannedDocuments)
            .extracting(doc -> doc.value.subtype)
            .containsExactlyElementsOf(envelope.documents.stream().map(d -> d.subtype).collect(toList()));
    }

    @Test
    public void from_envelope_handles_empty_document_list() {
        // given
        Envelope envelope = SampleData.envelope(0);

        // when
        SupplementaryEvidence result = mapper.mapEnvelope(envelope);

        // then
        assertThat(result.scannedDocuments).isEmpty();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
