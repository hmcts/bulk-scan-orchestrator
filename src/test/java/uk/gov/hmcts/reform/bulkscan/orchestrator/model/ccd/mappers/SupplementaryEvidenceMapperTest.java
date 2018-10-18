package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class SupplementaryEvidenceMapperTest {

    private static final ModelMapper<SupplementaryEvidence> mapper = new SupplementaryEvidenceMapper();

    @Test
    public void from_envelope_maps_all_fields_correctly() {
        Envelope envelope = SampleData.envelope(1);
        SupplementaryEvidence supplementaryEvidence = mapper.fromEnvelope(envelope);

        assertThat(supplementaryEvidence.scannedDocuments.size()).isEqualTo(1);

        Document envelopeDocument = envelope.documents.get(0);
        ScannedDocument scannedDocument = supplementaryEvidence.scannedDocuments.get(0).value;

        assertThat(scannedDocument.controlNumber).isEqualTo(envelopeDocument.controlNumber);
        assertThat(scannedDocument.fileName).isEqualTo(envelopeDocument.fileName);
        assertThat(scannedDocument.type).isEqualTo(envelopeDocument.type);
        assertThat(scannedDocument.url.documentUrl).isEqualTo(envelopeDocument.url);

        LocalDate expectedScannedDate =
            envelopeDocument.scannedAt.atZone(ZoneId.systemDefault()).toLocalDate();

        assertThat(scannedDocument.scannedDate).isEqualTo(expectedScannedDate);
    }

    @Test
    public void from_envelope_returns_supplementary_evidence_with_all_documents() {
        int numberOfDocuments = 12;
        Envelope envelope = SampleData.envelope(12);

        SupplementaryEvidence supplementaryEvidence = mapper.fromEnvelope(envelope);
        assertThat(supplementaryEvidence.scannedDocuments.size()).isEqualTo(numberOfDocuments);

        List<String> expectedDocumentFileNames =
            envelope.documents.stream().map(d -> d.fileName).collect(toList());

        List<String> actualDocumentFileNames =
            supplementaryEvidence.scannedDocuments.stream().map(d -> d.value.fileName).collect(toList());

        assertThat(actualDocumentFileNames).isEqualTo(expectedDocumentFileNames);
    }

    @Test
    public void from_envelope_handles_empty_document_list() {
        Envelope envelope = SampleData.envelope(0);
        SupplementaryEvidence supplementaryEvidence = mapper.fromEnvelope(envelope);

        assertThat(supplementaryEvidence.scannedDocuments).isEmpty();
    }
}
