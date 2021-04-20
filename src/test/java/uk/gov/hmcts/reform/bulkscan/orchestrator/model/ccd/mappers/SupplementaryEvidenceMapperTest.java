package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.LoggerTestUtil;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.EnvelopeReference;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EnvelopeReferenceHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static java.time.Instant.now;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;

@SuppressWarnings("checkstyle:LineLength")
@ExtendWith(MockitoExtension.class)
class SupplementaryEvidenceMapperTest {

    @Mock
    private EnvelopeReferenceHelper envelopeReferenceHelper;

    private SupplementaryEvidenceMapper mapper;
    Instant deliveryDate = Instant.now();

    private ListAppender<ILoggingEvent> loggingEvents;

    @BeforeEach
    void setUp() {
        mapper = new SupplementaryEvidenceMapper("http://localhost", "files", envelopeReferenceHelper);

        loggingEvents = LoggerTestUtil.getListAppenderForClass(SupplementaryEvidenceMapper.class);
    }

    @Test
    void maps_all_fields_correctly() {
        // given
        List<Document> existingDocs =
            asList(
                new Document("a.pdf", "aaa", "type_a", "subtype_a", now().plusSeconds(1), "uuida", now().minusSeconds(10)),
                new Document("b.pdf", "bbb", "type_b", "subtype_b", now().plusSeconds(2), "uuidb", now().minusSeconds(10))
            );

        List<Document> envelopeDocs =
            asList(
                new Document("x.pdf", "xxx", "type_x", "subtype_x", now().plusSeconds(3), "uuidx", now().minusSeconds(10)),
                new Document("y.pdf", "yyy", "type_y", "subtype_y", now().plusSeconds(4), "uuidy", now().minusSeconds(10))
            );

        // when
        SupplementaryEvidence result = mapper.map(existingDocs, emptyList(), envelope(envelopeDocs, now()));

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
                    ccdDoc.value.deliveryDate
                ))
            .containsExactly(
                tuple("a.pdf", "aaa", "type_a", "subtype_a", toLocalDateTime(existingDocs.get(0).scannedAt), "http://localhost/files/uuida", toLocalDateTime(existingDocs.get(0).deliveryDate)),
                tuple("b.pdf", "bbb", "type_b", "subtype_b", toLocalDateTime(existingDocs.get(1).scannedAt), "http://localhost/files/uuidb", toLocalDateTime(existingDocs.get(1).deliveryDate)),
                tuple("x.pdf", "xxx", "type_x", "subtype_x", toLocalDateTime(envelopeDocs.get(0).scannedAt), "http://localhost/files/uuidx", toLocalDateTime(envelopeDocs.get(0).deliveryDate)),
                tuple("y.pdf", "yyy", "type_y", "subtype_y", toLocalDateTime(envelopeDocs.get(1).scannedAt), "http://localhost/files/uuidy", toLocalDateTime(envelopeDocs.get(1).deliveryDate))
            );
        assertThat(loggingEvents.list)
            .extracting(ILoggingEvent::getMessage)
            .containsExactly(
                "Mapping documents: container bulkscan, zipFileName zip-file-test.zip, caseRef ABC123",
                "Existing docs: uuid: uuida, dcn: aaa, fileName: a.pdf; uuid: uuidb, dcn: bbb, fileName: b.pdf",
                "New docs: uuid: uuidx, dcn: xxx, fileName: x.pdf; uuid: uuidy, dcn: yyy, fileName: y.pdf",
                "Docs to add: uuid: uuidx, dcn: xxx, fileName: x.pdf; uuid: uuidy, dcn: yyy, fileName: y.pdf"
            );
    }

    @Test
    void should_not_add_document_from_envelope_if_document_with_the_same_url_is_already_present_in_case() {
        // given
        List<Document> existingDocs =
            asList(
                new Document("a.pdf", "aaa", "type_a", "subtype_a", now().plusSeconds(1), "uuida", now().minusSeconds(10)),
                new Document("b.pdf", "bbb", "type_b", "subtype_b", now().plusSeconds(2), "uuidb", now().minusSeconds(10))
            );

        List<Document> envelopeDocs =
            asList(
                new Document("a1.pdf", "aaa1", "type_a1", "subtype_a1", now().plusSeconds(3), "uuida", now().minusSeconds(10)), // same url!
                new Document("b.pdf", "bbb1", "type_b", "subtype_b", now().plusSeconds(4), "uuidxxxxx", now().minusSeconds(10)
            ));

        // when
        SupplementaryEvidence result = mapper.map(existingDocs, emptyList(), envelope(envelopeDocs, deliveryDate));

        // then
        assertThat(result.evidenceHandled).isEqualTo("No");
        assertThat(result.scannedDocuments).hasSize(3); // only one doc should be added
        assertThat(result.scannedDocuments)
            .extracting(ccdDoc ->
                tuple(ccdDoc.value.fileName, ccdDoc.value.url.documentUrl)
            )
            .containsExactly(
                tuple("a.pdf", "http://localhost/files/uuida"),
                tuple("b.pdf", "http://localhost/files/uuidb"),
                tuple("b.pdf", "http://localhost/files/uuidxxxxx")
            );
        assertThat(loggingEvents.list)
            .extracting(ILoggingEvent::getMessage)
            .containsExactly(
                "Mapping documents: container bulkscan, zipFileName zip-file-test.zip, caseRef ABC123",
                "Existing docs: uuid: uuida, dcn: aaa, fileName: a.pdf; uuid: uuidb, dcn: bbb, fileName: b.pdf",
                "New docs: uuid: uuida, dcn: aaa1, fileName: a1.pdf; uuid: uuidxxxxx, dcn: bbb1, fileName: b.pdf",
                "Docs to add: uuid: uuidxxxxx, dcn: bbb1, fileName: b.pdf"
            );
    }

    @SuppressWarnings("unchecked")
    @Test
    void should_set_envelope_references_correctly_when_service_supports_them() {
        // given
        var parsedExistingEnvelopeReferences =
            asList(new CcdCollectionElement<>(new EnvelopeReference("id1", CaseAction.CREATE)));

        given(envelopeReferenceHelper.serviceSupportsEnvelopeReferences(any())).willReturn(true);
        given(envelopeReferenceHelper.parseEnvelopeReferences(any())).willReturn(parsedExistingEnvelopeReferences);

        var rawExistingEnvelopeReferences = mock(List.class);
        var envelope = envelope(1);

        // when
        SupplementaryEvidence result = mapper.map(emptyList(), rawExistingEnvelopeReferences, envelope);

        // then
        List<CcdCollectionElement<EnvelopeReference>> expectedFinalEnvelopeReferences =
            getExpectedEnvelopeReferencesAfterUpdate(parsedExistingEnvelopeReferences, envelope.id);

        assertThat(result.bulkScanEnvelopes)
            .usingRecursiveFieldByFieldElementComparator()
            .isEqualTo(expectedFinalEnvelopeReferences);
        assertThat(loggingEvents.list)
            .extracting(ILoggingEvent::getMessage)
            .containsExactly(
                "Mapping documents: container bulkscan, zipFileName zip-file-test.zip, caseRef ABC123",
                "Update case: ABC123, zip file: zip-file-test.zip, envelope id: eb9c3598-35fc-424e-b05a-902ee9f11d56,"
                    + " existing case has bulkscan envelope id: id1, action: CREATE",
                "Existing docs: ",
                "New docs: uuid: uuid1, dcn: control_number_1, fileName: file_1.pdf",
                "Docs to add: uuid: uuid1, dcn: control_number_1, fileName: file_1.pdf"
            );

        verify(envelopeReferenceHelper).serviceSupportsEnvelopeReferences(envelope.container);
        verify(envelopeReferenceHelper).parseEnvelopeReferences(rawExistingEnvelopeReferences);
    }

    @SuppressWarnings("unchecked")
    @Test
    void should_set_envelope_references_to_null_when_service_does_not_support_them() {
        // given
        given(envelopeReferenceHelper.serviceSupportsEnvelopeReferences(any())).willReturn(false);

        var envelope = envelope(1);

        // when
        SupplementaryEvidence result = mapper.map(emptyList(), mock(List.class), envelope);

        // then
        assertThat(result.bulkScanEnvelopes).isNull();
        assertThat(loggingEvents.list)
            .extracting(ILoggingEvent::getMessage)
            .containsExactly(
                "Mapping documents: container bulkscan, zipFileName zip-file-test.zip, caseRef ABC123",
                "Existing docs: ",
                "New docs: uuid: uuid1, dcn: control_number_1, fileName: file_1.pdf",
                "Docs to add: uuid: uuid1, dcn: control_number_1, fileName: file_1.pdf"
            );

        verify(envelopeReferenceHelper).serviceSupportsEnvelopeReferences(envelope.container);
        verify(envelopeReferenceHelper, never()).parseEnvelopeReferences(any());
    }

    @Test
    void should_not_add_document_from_envelope_if_document_with_the_same_control_number_is_already_present_in_case() {
        // given
        List<Document> existingDocs =
            asList(
                new Document("a.pdf", "AAA", "type_a", "subtype_a", now().plusSeconds(1), "uuida", now().minusSeconds(10)),
                new Document("b.pdf", "BBB", "type_b", "subtype_b", now().plusSeconds(2), "uuidb", now().minusSeconds(10))
            );

        List<Document> envelopeDocs =
            asList(
                new Document("c.pdf", "AAA", "type_c", "subtype_c", now().plusSeconds(3), "uuidc", now().minusSeconds(10)), // same control number!
                new Document("d.pdf", "DDD", "type_d", "subtype_d", now().plusSeconds(4), "uuidd", now().minusSeconds(10))
            );

        // when
        SupplementaryEvidence result = mapper.map(existingDocs, emptyList(), envelope(envelopeDocs, deliveryDate));

        // then
        assertThat(result.scannedDocuments).hasSize(3); // only one doc should be added
        assertThat(result.scannedDocuments)
            .extracting(ccdDoc -> tuple(ccdDoc.value.fileName, ccdDoc.value.controlNumber))
            .containsExactly(
                tuple("a.pdf", "AAA"),
                tuple("b.pdf", "BBB"),
                tuple("d.pdf", "DDD")
            );
        assertThat(loggingEvents.list)
            .extracting(ILoggingEvent::getMessage)
            .containsExactly(
                "Mapping documents: container bulkscan, zipFileName zip-file-test.zip, caseRef ABC123",
                "Existing docs: uuid: uuida, dcn: AAA, fileName: a.pdf; uuid: uuidb, dcn: BBB, fileName: b.pdf",
                "New docs: uuid: uuidc, dcn: AAA, fileName: c.pdf; uuid: uuidd, dcn: DDD, fileName: d.pdf",
                "Docs to add: uuid: uuidd, dcn: DDD, fileName: d.pdf"
            );
    }

    @Test
    void handles_empty_document_list() {
        // given
        List<Document> existingDocuments = emptyList();
        List<Document> envelopeDocuments = emptyList();

        // when
        SupplementaryEvidence result = mapper.map(
            existingDocuments,
            emptyList(),
            envelope(envelopeDocuments, deliveryDate)
        );

        // then
        assertThat(result.scannedDocuments).isEmpty();
        assertThat(loggingEvents.list)
            .extracting(ILoggingEvent::getMessage)
            .containsExactly(
                "Mapping documents: container bulkscan, zipFileName zip-file-test.zip, caseRef ABC123",
                "Existing docs: ",
                "New docs: ",
                "Docs to add: "
            );
    }

    private List<CcdCollectionElement<EnvelopeReference>> getExpectedEnvelopeReferencesAfterUpdate(
        List<CcdCollectionElement<EnvelopeReference>> existingEnvelopeReferences,
        String newEnvelopeId
    ) {
        var references = newArrayList(existingEnvelopeReferences);
        references.add(new CcdCollectionElement<>(new EnvelopeReference(newEnvelopeId, CaseAction.UPDATE)));
        return references;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
