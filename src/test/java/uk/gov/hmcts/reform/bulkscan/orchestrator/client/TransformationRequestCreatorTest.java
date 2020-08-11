package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import io.vavr.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.DocumentUrl;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ScannedDocument;
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
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.client.SampleData.sampleExceptionRecord;

class TransformationRequestCreatorTest {

    private static final String DOCUMENT_MANAGEMENT_URL = "docManagementUrl1";
    private static final String DOCUMENT_MANAGEMENT_CONTEXT_PATH = "docManagementContextPath1";

    private TransformationRequestCreator requestCreator;

    @BeforeEach
    void setUp() {
        this.requestCreator = new TransformationRequestCreator(
            DOCUMENT_MANAGEMENT_URL,
            DOCUMENT_MANAGEMENT_CONTEXT_PATH
        );
    }

    @Test
    void should_set_all_fields_in_the_request_correctly_from_exception_record() {
        // given
        var exceptionRecord = sampleExceptionRecord();

        // when
        var transformationRequest = requestCreator.create(exceptionRecord);

        // then
        assertThat(transformationRequest)
            .usingRecursiveComparison()
            .ignoringFields("exceptionRecordCaseTypeId", "exceptionRecordId", "isAutomatedProcess")
            .isEqualTo(exceptionRecord);

        assertThat(transformationRequest.isAutomatedProcess).isFalse();
        assertThat(transformationRequest.exceptionRecordCaseTypeId).isEqualTo(exceptionRecord.caseTypeId);
        assertThat(transformationRequest.exceptionRecordId).isEqualTo(exceptionRecord.id);
    }

    @Test
    void should_set_all_fields_in_the_request_correctly_from_envelope() {
        // given
        var envelope = sampleEnvelope(sampleEnvelopeOcrDataFields());

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

        assertScannedDocumentsMappedCorrectly(transformationRequest.scannedDocuments, envelope.documents);
    }

    @Test
    void should_map_null_ocr_data_from_envelope_to_empty_list() {
        // given
        var envelope = sampleEnvelope(null);

        // when
        var transformationRequest = requestCreator.create(envelope);

        // then
        assertThat(transformationRequest.ocrDataFields).isEmpty();
    }

    private void assertScannedDocumentsMappedCorrectly(
        List<ScannedDocument> scannedDocuments,
        List<Document> envelopeDocuments
    ) {
        assertThat(scannedDocuments)
            .extracting(document ->
                Tuple.of(
                    document.controlNumber,
                    document.deliveryDate,
                    document.documentUrl,
                    document.fileName,
                    document.scannedDate,
                    document.subtype,
                    document.type.toString()
                )
            )
            .usingRecursiveFieldByFieldElementComparator()
            .isEqualTo(
                envelopeDocuments
                    .stream()
                    .map(document ->
                        Tuple.of(
                            document.controlNumber,
                            toLocalDateTime(document.deliveryDate),
                            getDocumentUrl(document),
                            document.fileName,
                            toLocalDateTime(document.scannedAt),
                            document.subtype,
                            document.type
                        )
                    )
                    .collect(toList())
            );
    }

    private DocumentUrl getDocumentUrl(Document document) {
        String url = String.join(
            "/",
            DOCUMENT_MANAGEMENT_URL,
            DOCUMENT_MANAGEMENT_CONTEXT_PATH,
            document.uuid
        );

        String binaryUrl = url + "/binary";

        return new DocumentUrl(url, binaryUrl, document.fileName);
    }

    private Envelope sampleEnvelope(List<OcrDataField> ocrDataFields) {
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
            sampleEnvelopeDocuments(),
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
                Instant.now().plusSeconds(3)
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
        return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }
}
