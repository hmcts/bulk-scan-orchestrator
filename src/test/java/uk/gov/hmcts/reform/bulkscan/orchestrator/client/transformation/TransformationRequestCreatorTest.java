package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.shared.DocumentMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.OcrDataField;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleExceptionRecord;

@ExtendWith(MockitoExtension.class)
class TransformationRequestCreatorTest {

    @Mock DocumentMapper documentMapper;

    private TransformationRequestCreator requestCreator;

    @BeforeEach
    void setUp() {
        this.requestCreator = new TransformationRequestCreator(documentMapper);
    }

    @Test
    void should_set_all_fields_in_the_request_correctly_from_exception_record() {
        // given
        var exceptionRecord = sampleExceptionRecord();

        // when
        var transformationRequest = requestCreator.create(exceptionRecord, false);

        // then
        assertThat(transformationRequest)
            .usingRecursiveComparison()
            .ignoringFields("exceptionRecordCaseTypeId", "exceptionRecordId", "isAutomatedProcess", "ignoreWarnings")
            .isEqualTo(exceptionRecord);

        assertThat(transformationRequest.isAutomatedProcess).isFalse();
        assertThat(transformationRequest.exceptionRecordCaseTypeId).isEqualTo(exceptionRecord.caseTypeId);
        assertThat(transformationRequest.exceptionRecordId).isEqualTo(exceptionRecord.id);
    }

    @Test
    void should_set_all_fields_in_the_request_correctly_from_envelope() {
        // given
        var doc1 = mock(Document.class);
        var doc2 = mock(Document.class);
        var envelope = sampleEnvelope(
            sampleEnvelopeOcrDataFields(),
            asList(doc1, doc2)
        );

        var mappedDoc1 = mock(ScannedDocument.class);
        var mappedDoc2 = mock(ScannedDocument.class);

        given(documentMapper.toScannedDoc(doc1)).willReturn(mappedDoc1);
        given(documentMapper.toScannedDoc(doc2)).willReturn(mappedDoc2);

        // when
        var transformationRequest = requestCreator.create(envelope);

        // then
        assertThat(transformationRequest.exceptionRecordId).isNull();
        assertThat(transformationRequest.id).isNull();
        assertThat(transformationRequest.exceptionRecordCaseTypeId).isNull();
        assertThat(transformationRequest.caseTypeId).isNull();
        assertThat(transformationRequest.isAutomatedProcess).isTrue();
        assertThat(transformationRequest.deliveryDate).isEqualTo(toLocalDateTime(envelope.deliveryDate));
        assertThat(transformationRequest.envelopeId).isEqualTo(envelope.id);
        assertThat(transformationRequest.formType).isEqualTo(envelope.formType);
        assertThat(transformationRequest.journeyClassification).isEqualTo(envelope.classification);
        assertThat(transformationRequest.openingDate).isEqualTo(toLocalDateTime(envelope.openingDate));
        assertThat(transformationRequest.poBox).isEqualTo(envelope.poBox);
        assertThat(transformationRequest.poBoxJurisdiction).isEqualTo(envelope.jurisdiction);

        assertThat(transformationRequest.ocrDataFields)
            .usingFieldByFieldElementComparator()
            .isEqualTo(envelope.ocrData);

        assertThat(transformationRequest.scannedDocuments).hasSize(2);
        assertThat(transformationRequest.scannedDocuments.get(0)).isEqualTo(mappedDoc1);
        assertThat(transformationRequest.scannedDocuments.get(1)).isEqualTo(mappedDoc2);
    }

    @Test
    void should_map_null_ocr_data_from_envelope_to_empty_list() {
        // given
        var envelope = sampleEnvelope(null, sampleEnvelopeDocuments());

        // when
        var transformationRequest = requestCreator.create(envelope);

        // then
        assertThat(transformationRequest.ocrDataFields).isEmpty();
    }

    private Envelope sampleEnvelope(List<OcrDataField> ocrDataFields, List<Document> documents) {
        return new Envelope(
            "envelopeId1",
            "caseRef1",
            "legacyCaseRef1",
            "poBox1",
            "jurisdiction1",
            "container1",
            "zipFileName1",
            "formType1",
            Instant.now(),
            Instant.now().plusSeconds(1),
            Classification.NEW_APPLICATION,
            documents,
            emptyList(),
            ocrDataFields,
            emptyList()
        );
    }

    private List<Document> sampleEnvelopeDocuments() {
        return asList(
            new Document(
                "fileName1",
                "controlNumber1",
                "form",
                "subtype1",
                Instant.now(),
                "uuid1",
                Instant.now().plusSeconds(1)
            ),
            new Document(
                "fileName2",
                "controlNumber2",
                "other",
                "subtype2",
                Instant.now().plusSeconds(2),
                "uuid1",
                null
            )
        );
    }

    private List<OcrDataField> sampleEnvelopeOcrDataFields() {
        return asList(
            new OcrDataField("name1", "value1"),
            new OcrDataField("name2", "value2")
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
