package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class SupplementaryEvidenceMapperTest {

    private static final SupplementaryEvidenceMapper mapper = new SupplementaryEvidenceMapper();

    @Test
    public void from_envelope_maps_all_fields_correctly() {
        // given
        Envelope envelope = SampleData.envelope(5);

        // when
        SupplementaryEvidence result = mapper.mapEnvelope(envelope);

        // then
        assertThat(result.evidenceHandled).isEqualTo("No");
        assertThat(result.scannedDocuments).hasSize(5);
        assertThat(result.scannedDocuments)
            .extracting(doc ->
                tuple(
                    doc.value.fileName,
                    doc.value.controlNumber,
                    doc.value.type,
                    doc.value.subtype,
                    doc.value.url.documentUrl,
                    doc.value.scannedDate
                ))
            .containsExactlyElementsOf(envelope.documents.stream().map(doc ->
                tuple(
                    doc.fileName,
                    doc.controlNumber,
                    doc.type,
                    doc.subtype,
                    doc.url,
                    toLocalDateTime(doc.scannedAt)
                )).collect(toList())
            );
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
