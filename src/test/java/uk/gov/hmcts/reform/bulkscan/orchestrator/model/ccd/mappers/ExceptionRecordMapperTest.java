package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.shared.DocumentMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Payment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;

@ExtendWith(MockitoExtension.class)
class ExceptionRecordMapperTest {

    private static final String DOCUMENT_MANAGEMENT_URL = "https://example.gov.uk";
    private static final String CONTEXT_PATH = "files";

    @Mock
    private ServiceConfigProvider serviceConfigProvider;
    @Mock
    private ServiceConfigItem serviceConfigItem;

    @Mock
    private DocumentMapper documentMapper;

    @Captor
    private ArgumentCaptor<List<Document>> captor;

    @InjectMocks
    private ExceptionRecordMapper mapper;

    @Test
    void mapEnvelope_maps_all_fields_correctly() {
        // given
        mockServiceConfig();
        Envelope envelope = envelope(
            2,
            ImmutableList.of(new Payment("dcn1")),
            ImmutableList.of(
                new OcrDataField("fieldName1", "value1"),
                new OcrDataField("field_surname", "surname1")
            ),
            asList("warning 1", "warning 2")
        );

        given(documentMapper.mapToCcdScannedDocuments(anyList(), anyList(), any(Instant.class), anyString()))
            .willReturn(
                asList(
                    getScannedDocumentCcdCollectionElement(envelope.documents.get(0)),
                    getScannedDocumentCcdCollectionElement(envelope.documents.get(1))
                )
            );

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
            .usingRecursiveFieldByFieldElementComparator()
            .containsAll(envelope.documents);

        assertThat(ocrDataAsList(exceptionRecord.ocrData))
            .usingRecursiveFieldByFieldElementComparator()
            .containsAll(envelope.ocrData);

        assertThat(toEnvelopeOcrDataWarnings(exceptionRecord.ocrDataValidationWarnings))
            .usingRecursiveFieldByFieldElementComparator()
            .containsExactlyElementsOf(envelope.ocrDataValidationWarnings);

        assertThat(exceptionRecord.envelopeId).isEqualTo(envelope.id);
        assertThat(exceptionRecord.envelopeCaseReference).isEqualTo(envelope.caseRef);
        assertThat(exceptionRecord.envelopeLegacyCaseReference).isEqualTo(envelope.legacyCaseRef);
        assertThat(exceptionRecord.showEnvelopeCaseReference).isEqualTo("No"); // for "New Application"
        assertThat(exceptionRecord.showEnvelopeLegacyCaseReference).isEqualTo("No"); // for "New Application"
        assertThat(exceptionRecord.surname).isEqualTo("surname1");

        verify(documentMapper).mapToCcdScannedDocuments(
            eq(emptyList()),
            captor.capture(),
            any(Instant.class),
            eq(envelope.jurisdiction)
        );

        List<Document> docList = captor.getValue();
        assertThat(docList).usingRecursiveComparison().isEqualTo(envelope.documents);
    }

    @Test
    void mapEnvelope_handles_null_ocr_data() {
        Envelope envelope = envelope(2, emptyList(), null, emptyList());
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);
        assertThat(exceptionRecord.ocrData).isNull();
        assertThat(exceptionRecord.surname).isNull();
    }

    @Test
    void mapEnvelope_maps_subtype_values_in_documents() {
        // given
        Envelope envelope = envelope(2, null, emptyList(), emptyList());

        given(documentMapper.mapToCcdScannedDocuments(anyList(), anyList(), any(Instant.class), anyString()))
            .willReturn(
                asList(
                    getScannedDocumentCcdCollectionElement(envelope.documents.get(0)),
                    getScannedDocumentCcdCollectionElement(envelope.documents.get(1))
                )
            );

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

    @Test
    void mapEnvelope_sets_warnings_presence_correctly() {
        Envelope envelopeWithWarning = envelope(2, null, emptyList(), newArrayList("Warning"));
        Envelope envelopeWithoutWarning = envelope(2, null, emptyList(), emptyList());

        assertThat(mapper.mapEnvelope(envelopeWithWarning).displayWarnings).isEqualTo("Yes");
        assertThat(mapper.mapEnvelope(envelopeWithoutWarning).displayWarnings).isEqualTo("No");
    }

    @Test
    void mapEnvelope_copies_envelope_id_to_exception_record() {
        // given
        mockServiceConfig();

        String supportedJurisdiction = "supported-jurisdiction1";

        // when
        Envelope envelope = envelopeWithJurisdiction(supportedJurisdiction);

        // then
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        assertThat(exceptionRecord.envelopeId).isEqualTo(envelope.id);
    }

    @Test
    void mapEnvelope_sets_payment_fields_to_yes_when_envelope_contains_payments() {
        //given
        Envelope envelope = envelope(
            2,
            ImmutableList.of(new Payment("dcn1")),
            null,
            emptyList()
        );

        //when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        //then
        assertThat(exceptionRecord.awaitingPaymentDcnProcessing).isEqualTo("Yes");
        assertThat(exceptionRecord.containsPayments).isEqualTo("Yes");
    }

    @Test
    void mapEnvelope_sets_payment_fields_to_no_when_envelope_does_not_contain_payments() {
        //given
        Envelope envelope = envelope(2, null, null, emptyList());

        //when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        //then
        assertThat(exceptionRecord.awaitingPaymentDcnProcessing).isEqualTo("No");
        assertThat(exceptionRecord.containsPayments).isEqualTo("No");
    }

    @Test
    void mapEnvelope_sets_display_case_reference_fields_to_no_when_envelope_case_reference_values_are_null() {
        //given
        Envelope envelope = envelope(null, null, Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR);
        mockServiceConfig();
        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.envelopeCaseReference).isEmpty();
        assertThat(exceptionRecord.envelopeLegacyCaseReference).isEmpty();
        assertThat(exceptionRecord.showEnvelopeCaseReference).isEqualTo("No");
        assertThat(exceptionRecord.showEnvelopeLegacyCaseReference).isEqualTo("No");
    }

    @Test
    void mapEnvelope_sets_display_case_reference_fields_to_yes_when_envelope_case_reference_values_exist() {
        //given
        Envelope envelope = envelope("CASE_123", "LEGACY_CASE_123", Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR);
        mockServiceConfig();
        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.envelopeCaseReference).isEqualTo("CASE_123");
        assertThat(exceptionRecord.envelopeLegacyCaseReference).isEqualTo("LEGACY_CASE_123");
        assertThat(exceptionRecord.showEnvelopeCaseReference).isEqualTo("Yes");
        assertThat(exceptionRecord.showEnvelopeLegacyCaseReference).isEqualTo("Yes");
    }

    @Test
    void mapEnvelope_sets_surname_null_when_no_surname_data_in_ocr() {
        mockServiceConfig();
        //given
        Envelope envelope = envelope("CASE_123", "LEGACY_CASE_123", Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR);
        given(serviceConfigProvider.getConfig("bulkscan")).willReturn(serviceConfigItem);
        given(serviceConfigItem.getSurnameOcrFieldNameList("FORM_TYPE"))
            .willReturn(singletonList("field_surname"));

        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.surname).isNull();
    }

    @Test
    void mapEnvelope_sets_first_surname_when_multiple_surname_data_in_ocr() {
        mockServiceConfig();
        //given
        Envelope envelope = envelope(
            1,
            ImmutableList.of(new Payment("dcn1")),
            ImmutableList.of(
                new OcrDataField("fieldName1", "value1"),
                new OcrDataField("field_surname", "surname_a"),
                new OcrDataField("field_surname", "surname_1"),
                new OcrDataField("field_surname", "surname_2")
            ),
            asList("warning 1", "warning 2")
        );        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.surname).isEqualTo("surname_a");
    }

    @Test
    void mapEnvelope_sets_non_empty_surname_when_ocr_surname_data_empty() {
        mockServiceConfig();
        //given
        Envelope envelope = envelope(
            1,
            ImmutableList.of(new Payment("dcn1")),
            ImmutableList.of(
                new OcrDataField("field_surname", "   "),
                new OcrDataField("field_surname", ""),
                new OcrDataField("field_surname", null),
                new OcrDataField("field_surname", "surname_2")
            ),
            asList("warning 1", "warning 2")
        );        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.surname).isEqualTo("surname_2");
    }

    @Test
    void mapEnvelope_sets_surname_with_first_matching_ocr_conf_when_multiple_conf_available() {
        //given
        given(serviceConfigItem.getSurnameOcrFieldNameList("FORM_TYPE"))
            .willReturn(asList("field_surname_not_found", "field_surname", "fieldName1"));
        mockServiceConfig();

        Envelope envelope = envelope(
            1,
            ImmutableList.of(new Payment("dcn1")),
            ImmutableList.of(
                new OcrDataField("fieldName1", "value1"),
                new OcrDataField("field_surname_2", "surname_a"),
                new OcrDataField("field_surname", "surname_1"),
                new OcrDataField("field_surname", "surname_2")
            ),
            asList("warning 1", "warning 2")
        );        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.surname).isEqualTo("surname_1");
    }

    @Test
    void mapEnvelope_sets_surname_when_both_ocr_cof_available_by_using_first_surname_configuration() {
        //given
        given(serviceConfigItem.getSurnameOcrFieldNameList("FORM_TYPE"))
            .willReturn(asList("field_surname", "fieldName1"));
        mockServiceConfig();
        Envelope envelope = envelope(
            1,
            ImmutableList.of(new Payment("dcn1")),
            ImmutableList.of(
                new OcrDataField("fieldName1", "value1"),
                new OcrDataField("field_surname", "surname_x")
            ),
            asList("warning 1", "warning 2")
        );        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.surname).isEqualTo("surname_x");
    }

    private void mockServiceConfig() {
        given(serviceConfigProvider.getConfig("bulkscan")).willReturn(serviceConfigItem);
        given(serviceConfigItem.getSurnameOcrFieldNameList("FORM_TYPE"))
            .willReturn(singletonList("field_surname"));
    }

    private Envelope envelopeWithJurisdiction(String jurisdiction) {
        return envelope(Classification.NEW_APPLICATION, jurisdiction, "1231231232312765");
    }

    private List<OcrDataField> ocrDataAsList(List<CcdCollectionElement<CcdKeyValue>> ocrData) {
        return ocrData
            .stream()
            .map(element -> new OcrDataField(element.value.key, element.value.value))
            .collect(Collectors.toList());
    }

    private List<String> toEnvelopeOcrDataWarnings(List<CcdCollectionElement<String>> ocrDataValidationWarnings) {
        return ocrDataValidationWarnings
            .stream()
            .map(e -> e.value)
            .collect(toList());
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
                    StringUtils.substringAfterLast(scannedDocument.url.documentUrl, "/"),
                    scannedDocument.deliveryDate.atZone(ZoneId.systemDefault()).toInstant()
                )
            ).collect(toList());
    }

    private CcdCollectionElement<ScannedDocument> getScannedDocumentCcdCollectionElement(Document doc) {
        return new CcdCollectionElement<>(
                new ScannedDocument(
                        doc.fileName,
                        doc.controlNumber,
                        doc.type,
                        doc.subtype,
                        ZonedDateTime.ofInstant(doc.scannedAt, ZoneId.systemDefault()).toLocalDateTime(),
                        new CcdDocument(String.join("/", DOCUMENT_MANAGEMENT_URL, CONTEXT_PATH, doc.uuid), null),
                        ZonedDateTime.ofInstant(doc.deliveryDate, ZoneId.systemDefault()).toLocalDateTime(),
                        null
                )
        );
    }
}
