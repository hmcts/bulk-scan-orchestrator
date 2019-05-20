package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static java.time.Instant.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SuppressWarnings("checkstyle:LineLength")
class SupplementaryEvidenceMapperTest {

    private static final SupplementaryEvidenceMapper mapper = new SupplementaryEvidenceMapper();

    @Test
    void maps_all_fields_correctly() {
        // given
        List<Document> existingDocs =
            asList(
                new Document("a.pdf", "aaa", "type_a", "subtype_a", now().plusSeconds(1), "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcba", "0fa1ab60-f836-43aa-8c65-b07cc9bebcba"),
                new Document("b.pdf", "bbb", "type_b", "subtype_b", now().plusSeconds(2), "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcbb", "0fa1ab60-f836-43aa-8c65-b07cc9bebcbb")
            );

        List<Document> envelopeDocs =
            asList(
                new Document("x.pdf", "xxx", "type_x", "subtype_x", now().plusSeconds(3), "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcbx", "0fa1ab60-f836-43aa-8c65-b07cc9bebcbx"),
                new Document("y.pdf", "yyy", "type_y", "subtype_y", now().plusSeconds(4), "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcby", "0fa1ab60-f836-43aa-8c65-b07cc9bebcby")
            );

        // when
        SupplementaryEvidence result = mapper.map(existingDocs, envelopeDocs);

        // then
        assertThat(result.evidenceHandled).isEqualTo("No");
        assertThat(result.scannedDocuments).hasSize(4);
        assertThat(result.scannedDocuments)
            .extracting(ccdDoc ->
                tuple(
                    ccdDoc.value.fileName,
                    ccdDoc.value.controlNumber,
                    ccdDoc.value.type,
                    ccdDoc.value.subtype,
                    ccdDoc.value.scannedDate,
                    ccdDoc.value.url.documentUrl
                ))
            .containsExactly(
                tuple("a.pdf", "aaa", "type_a", "subtype_a", toLocalDateTime(existingDocs.get(0).scannedAt), "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcba"),
                tuple("b.pdf", "bbb", "type_b", "subtype_b", toLocalDateTime(existingDocs.get(1).scannedAt), "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcbb"),
                tuple("x.pdf", "xxx", "type_x", "subtype_x", toLocalDateTime(envelopeDocs.get(0).scannedAt), "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcbx"),
                tuple("y.pdf", "yyy", "type_y", "subtype_y", toLocalDateTime(envelopeDocs.get(1).scannedAt), "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcby")
            );
    }

    @Test
    void should_not_add_document_from_envelope_if_document_with_the_same_url_is_already_present_in_case() {
        // given
        List<Document> existingDocs =
            asList(
                new Document("a.pdf", "aaa", "type_a", "subtype_a", now().plusSeconds(1), "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcaa", "0fa1ab60-f836-43aa-8c65-b07cc9bebcaa"),
                new Document("b.pdf", "bbb", "type_b", "subtype_b", now().plusSeconds(2), "https://example.gov.uk/5a76f366-38a1-45e5-a424-f1658f258968", "5a76f366-38a1-45e5-a424-f1658f258968")
            );

        List<Document> envelopeDocs =
            asList(
                new Document("a1.pdf", "aaa1", "type_a1", "subtype_a1", now().plusSeconds(3), "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcaa", "0fa1ab60-f836-43aa-8c65-b07cc9bebcaa"), // same url!
                new Document("b.pdf", "bbb1", "type_b", "subtype_b", now().plusSeconds(4), "http://example.gov.uk/16abb504-a837-48c6-8b8a-07557230eecd", "16abb504-a837-48c6-8b8a-07557230eecd")
            );

        // when
        SupplementaryEvidence result = mapper.map(existingDocs, envelopeDocs);

        // then
        assertThat(result.evidenceHandled).isEqualTo("No");
        assertThat(result.scannedDocuments).hasSize(3); // only one doc should be added
        assertThat(result.scannedDocuments)
            .extracting(ccdDoc ->
                tuple(ccdDoc.value.fileName, ccdDoc.value.url.documentUrl)
            )
            .containsExactly(
                tuple("a.pdf", "https://example.gov.uk/0fa1ab60-f836-43aa-8c65-b07cc9bebcaa"),
                tuple("b.pdf", "https://example.gov.uk/5a76f366-38a1-45e5-a424-f1658f258968"),
                tuple("b.pdf", "http://example.gov.uk/16abb504-a837-48c6-8b8a-07557230eecd")
            );
    }

    @Test
    void should_not_add_document_from_envelope_if_document_with_the_same_control_number_is_already_present_in_case() {
        // given
        List<Document> existingDocs =
            asList(
                new Document("a.pdf", "AAA", "type_a", "subtype_a", now().plusSeconds(1), "https://example.gov.uk/eb2b0c2e-83ff-4f6e-9b2c-ac57b3b0358b", "eb2b0c2e-83ff-4f6e-9b2c-ac57b3b0358b"),
                new Document("b.pdf", "BBB", "type_b", "subtype_b", now().plusSeconds(2), "https://example.gov.uk/f8a5f699-3816-4298-9365-ecb5b634c8f6", "f8a5f699-3816-4298-9365-ecb5b634c8f6")
            );

        List<Document> envelopeDocs =
            asList(
                new Document("c.pdf", "AAA", "type_c", "subtype_c", now().plusSeconds(3), "https://example.gov.uk/adee023f-edf7-4e4c-bf36-d73da1ab48b5", "adee023f-edf7-4e4c-bf36-d73da1ab48b5"), // same control number!
                new Document("d.pdf", "DDD", "type_d", "subtype_d", now().plusSeconds(4), "https://example.gov.uk/863c495e-d05b-4376-9951-ea489360db6f", "863c495e-d05b-4376-9951-ea489360db6f")
            );

        // when
        SupplementaryEvidence result = mapper.map(existingDocs, envelopeDocs);

        // then
        assertThat(result.scannedDocuments).hasSize(3); // only one doc should be added
        assertThat(result.scannedDocuments)
            .extracting(ccdDoc -> tuple(ccdDoc.value.fileName, ccdDoc.value.controlNumber))
            .containsExactly(
                tuple("a.pdf", "AAA"),
                tuple("b.pdf", "BBB"),
                tuple("d.pdf", "DDD")
            );
    }

    @Test
    void handles_empty_document_list() {
        // given
        List<Document> existingDocuments = emptyList();
        List<Document> envelopeDocuments = emptyList();

        // when
        SupplementaryEvidence result = mapper.map(existingDocuments, envelopeDocuments);

        // then
        assertThat(result.scannedDocuments).isEmpty();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
