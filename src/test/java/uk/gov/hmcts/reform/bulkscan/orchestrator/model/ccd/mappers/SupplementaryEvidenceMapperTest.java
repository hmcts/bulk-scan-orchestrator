package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class SupplementaryEvidenceMapperTest {

    private static final ModelMapper<SupplementaryEvidence> mapper = new SupplementaryEvidenceMapper();

    @Test
    public void from_envelope_maps_all_fields_correctly() {
        Envelope envelope = SampleData.envelope(1);
        SupplementaryEvidence supplementaryEvidence = mapper.mapEnvelope(envelope);

        assertThat(supplementaryEvidence.evidenceHandled).isEqualTo("No");

        assertThat(supplementaryEvidence.scannedDocuments.size()).isEqualTo(1);

        Document envelopeDocument = envelope.documents.get(0);
        ScannedDocument scannedDocument = supplementaryEvidence.scannedDocuments.get(0).value;

        assertThat(scannedDocument.controlNumber).isEqualTo(envelopeDocument.controlNumber);
        assertThat(scannedDocument.fileName).isEqualTo(envelopeDocument.fileName);
        assertThat(scannedDocument.type).isEqualTo(envelopeDocument.subtype);
        assertThat(scannedDocument.url.documentUrl).isEqualTo(envelopeDocument.url);

        LocalDateTime expectedScannedDate =
            envelopeDocument.scannedAt.atZone(ZoneId.systemDefault()).toLocalDateTime();

        assertThat(scannedDocument.scannedDate).isEqualTo(expectedScannedDate);
    }

    @Test
    public void from_envelope_returns_supplementary_evidence_with_all_documents() {
        int numberOfDocuments = 12;
        Envelope envelope = SampleData.envelope(12);

        SupplementaryEvidence supplementaryEvidence = mapper.mapEnvelope(envelope);
        assertThat(supplementaryEvidence.scannedDocuments.size()).isEqualTo(numberOfDocuments);

        List<String> expectedDocumentFileNames =
            envelope.documents.stream().map(d -> d.fileName).collect(toList());

        List<String> actualDocumentFileNames =
            supplementaryEvidence.scannedDocuments.stream().map(d -> d.value.fileName).collect(toList());

        assertThat(actualDocumentFileNames).isEqualTo(expectedDocumentFileNames);
    }


    @Test
    public void map_envelope_maps_all_documents_with_subtype_values_copied_to_type() {
        int numberOfDocuments = 2;
        Envelope envelope = SampleData.envelope(2, null, true);

        SupplementaryEvidence supplementaryEvidence = mapper.mapEnvelope(envelope);
        assertThat(supplementaryEvidence.scannedDocuments.size()).isEqualTo(numberOfDocuments);

        List<String> expectedDocumentSubtypeValues =
            envelope.documents.stream().map(d -> d.subtype).collect(toList());

        List<String> actualDocumentTypeValues =
            supplementaryEvidence.scannedDocuments.stream().map(d -> d.value.type).collect(toList());

        List<String> actualDocumentSubtypeValues =
            supplementaryEvidence.scannedDocuments
                .stream()
                .filter(d -> StringUtils.isNotEmpty(d.value.type))
                .map(d -> d.value.subtype)
                .collect(toList());

        assertThat(actualDocumentTypeValues).isEqualTo(expectedDocumentSubtypeValues);
        assertThat(actualDocumentSubtypeValues).containsExactly(null, null);
    }

    @Test
    public void map_envelope_maps_all_documents_with_subtype_values_null() {
        int numberOfDocuments = 2;
        Envelope envelope = SampleData.envelope(2, null, false);

        SupplementaryEvidence supplementaryEvidence = mapper.mapEnvelope(envelope);
        assertThat(supplementaryEvidence.scannedDocuments.size()).isEqualTo(numberOfDocuments);

        List<String> expectedDocumentTypeValues =
            envelope.documents.stream().map(d -> d.type).collect(toList());

        List<String> expectedDocumentSubtypeValues =
            envelope.documents.stream().map(d -> d.subtype).collect(toList());

        List<String> actualDocumentTypeValues =
            supplementaryEvidence.scannedDocuments.stream().map(d -> d.value.type).collect(toList());

        List<String> actualDocumentSubtypeValues =
            envelope.documents.stream().map(d -> d.subtype).collect(toList());

        assertThat(expectedDocumentSubtypeValues).filteredOn(StringUtils::isEmpty).hasSize(numberOfDocuments);
        assertThat(actualDocumentSubtypeValues).filteredOn(StringUtils::isEmpty).hasSize(numberOfDocuments);

        assertThat(actualDocumentTypeValues).isEqualTo(expectedDocumentTypeValues);
    }

    @Test
    public void from_envelope_handles_empty_document_list() {
        Envelope envelope = SampleData.envelope(0);
        SupplementaryEvidence supplementaryEvidence = mapper.mapEnvelope(envelope);

        assertThat(supplementaryEvidence.scannedDocuments).isEmpty();
    }
}
