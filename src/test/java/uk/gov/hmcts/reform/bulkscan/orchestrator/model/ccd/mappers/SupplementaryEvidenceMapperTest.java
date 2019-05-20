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
                new Document(
                    "a.pdf", "aaa", "type_a", "subtype_a", now().plusSeconds(1), "http://localhost/a.pdf", "uuida"
                ),
                new Document(
                    "b.pdf", "bbb", "type_b", "subtype_b", now().plusSeconds(2), "http://localhost/b.pdf", "uuidb"
                )
            );

        List<Document> envelopeDocs =
            asList(
                new Document("x.pdf", "xxx", "type_x", "subtype_x", now().plusSeconds(3), "http://localhost/x.pdf", "uuidx"),
                new Document("y.pdf", "yyy", "type_y", "subtype_y", now().plusSeconds(4), "http://localhost/y.pdf", "uuidy")
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
                    ccdDoc.value.url.documentUrl,
                    ccdDoc.value.uuid
                ))
            .containsExactly(
                tuple("a.pdf", "aaa", "type_a", "subtype_a", toLocalDateTime(existingDocs.get(0).scannedAt), "http://localhost/a.pdf", "uuida"),
                tuple("b.pdf", "bbb", "type_b", "subtype_b", toLocalDateTime(existingDocs.get(1).scannedAt), "http://localhost/b.pdf", "uuidb"),
                tuple("x.pdf", "xxx", "type_x", "subtype_x", toLocalDateTime(envelopeDocs.get(0).scannedAt), "http://localhost/x.pdf", "uuidx"),
                tuple("y.pdf", "yyy", "type_y", "subtype_y", toLocalDateTime(envelopeDocs.get(1).scannedAt), "http://localhost/y.pdf", "uuidy")
            );
    }

    @Test
    void should_not_add_document_from_envelope_if_document_with_the_same_url_is_already_present_in_case() {
        // given
        List<Document> existingDocs =
            asList(
                new Document(
                    "a.pdf", "aaa", "type_a", "subtype_a", now().plusSeconds(1), "http://localhost/a.pdf", "a.pdf"),
                new Document(
                    "b.pdf", "bbb", "type_b", "subtype_b", now().plusSeconds(2), "http://localhost/b.pdf", "b.pdf")
            );

        List<Document> envelopeDocs =
            asList(
                new Document(
                    "a1.pdf", "aaa1", "type_a1", "subtype_a1", now().plusSeconds(3), "http://localhost/a.pdf", "uuida"
                ), // same url!
                new Document(
                    "b.pdf", "bbb1", "type_b", "subtype_b", now().plusSeconds(4), "http://localhost/xxxxx.pdf", "uuidxxxxx"
                )
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
                tuple("a.pdf", "http://localhost/a.pdf"),
                tuple("b.pdf", "http://localhost/b.pdf"),
                tuple("b.pdf", "http://localhost/xxxxx.pdf")
            );
    }

    @Test
    void should_not_add_document_from_envelope_if_document_with_the_same_control_number_is_already_present_in_case() {
        // given
        List<Document> existingDocs =
            asList(
                new Document("a.pdf", "AAA", "type_a", "subtype_a", now().plusSeconds(1), "http://localhost/a.pdf", "a.pdf"),
                new Document("b.pdf", "BBB", "type_b", "subtype_b", now().plusSeconds(2), "http://localhost/b.pdf", "b.pdf")
            );

        List<Document> envelopeDocs =
            asList(
                new Document("c.pdf", "AAA", "type_c", "subtype_c", now().plusSeconds(3), "http://localhost/c.pdf", "c.pdf"), // same control number!
                new Document("d.pdf", "DDD", "type_d", "subtype_d", now().plusSeconds(4), "http://localhost/d.pdf", "d.pdf")
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
