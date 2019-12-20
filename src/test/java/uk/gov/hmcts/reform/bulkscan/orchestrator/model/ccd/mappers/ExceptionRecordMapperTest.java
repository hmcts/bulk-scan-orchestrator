package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdCollectionElement;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CcdKeyValue;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.ScannedDocument;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Payment;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelope;

class ExceptionRecordMapperTest {

    private ServiceConfigProvider serviceConfigProvider = mock(ServiceConfigProvider.class);
    private ServiceConfigItem serviceConfigItem = mock(ServiceConfigItem.class);

    private final ExceptionRecordMapper mapper = new ExceptionRecordMapper(
        "https://example.gov.uk",
        "files",
        serviceConfigProvider
    );

    @BeforeEach
    void setupServiceConfig() {
        given(serviceConfigProvider.getConfig("bulkscan")).willReturn(serviceConfigItem);
        given(serviceConfigItem.getSurnameOcrFieldNameList("FORM_TYPE"))
            .willReturn(asList("field_surname"));
    }

    @Test
    public void mapEnvelope_maps_all_fields_correctly() {
        // given
        Envelope envelope = envelope(
            2,
            ImmutableList.of(new Payment("dcn1")),
            ImmutableList.of(
                new OcrDataField("fieldName1", "value1"),
                new OcrDataField("field_surname", "surname1")
            ),
            asList("warning 1", "warning 2")
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
            .usingFieldByFieldElementComparator()
            .containsAll(envelope.documents);

        assertThat(ocrDataAsList(exceptionRecord.ocrData))
            .usingFieldByFieldElementComparator()
            .containsAll(envelope.ocrData);

        assertThat(toEnvelopeOcrDataWarnings(exceptionRecord.ocrDataValidationWarnings))
            .usingFieldByFieldElementComparator()
            .containsExactlyElementsOf(envelope.ocrDataValidationWarnings);

        assertThat(exceptionRecord.envelopeId).isEqualTo(envelope.id);
        assertThat(exceptionRecord.envelopeCaseReference).isEqualTo(envelope.caseRef);
        assertThat(exceptionRecord.envelopeLegacyCaseReference).isEqualTo(envelope.legacyCaseRef);
        assertThat(exceptionRecord.showEnvelopeCaseReference).isEqualTo("No"); // for "New Application"
        assertThat(exceptionRecord.showEnvelopeLegacyCaseReference).isEqualTo("No"); // for "New Application"
        assertThat(exceptionRecord.surname).isEqualTo("surname1");
    }

    @Test
    public void mapEnvelope_handles_null_ocr_data() {
        Envelope envelope = envelope(2, emptyList(), null, emptyList());
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);
        assertThat(exceptionRecord.ocrData).isNull();
        assertThat(exceptionRecord.surname).isNull();
    }

    @Test
    public void mapEnvelope_maps_subtype_values_in_documents() {
        // given
        Envelope envelope = envelope(2, null, emptyList(), emptyList());

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
    public void mapEnvelope_sets_warnings_presence_correctly() {
        Envelope envelopeWithWarning = envelope(2, null, emptyList(), newArrayList("Warning"));
        Envelope envelopeWithoutWarning = envelope(2, null, emptyList(), emptyList());

        assertThat(mapper.mapEnvelope(envelopeWithWarning).displayWarnings).isEqualTo("Yes");
        assertThat(mapper.mapEnvelope(envelopeWithoutWarning).displayWarnings).isEqualTo("No");
    }

    @Test
    public void mapEnvelope_copies_envelope_id_to_exception_record() {
        // given
        String supportedJurisdiction = "supported-jurisdiction1";

        // when
        Envelope envelope = envelopeWithJurisdiction(supportedJurisdiction);

        // then
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        assertThat(exceptionRecord.envelopeId).isEqualTo(envelope.id);
    }

    @Test
    public void mapEnvelope_sets_payment_fields_to_yes_when_envelope_contains_payments() {
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
    public void mapEnvelope_sets_payment_fields_to_no_when_envelope_does_not_contain_payments() {
        //given
        Envelope envelope = envelope(2, null, null, emptyList());

        //when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        //then
        assertThat(exceptionRecord.awaitingPaymentDcnProcessing).isEqualTo("No");
        assertThat(exceptionRecord.containsPayments).isEqualTo("No");
    }

    @Test
    public void mapEnvelope_sets_display_case_reference_fields_to_no_when_envelope_case_reference_values_are_null() {
        //given
        Envelope envelope = envelope(null, null, Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR);

        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.envelopeCaseReference).isEmpty();
        assertThat(exceptionRecord.envelopeLegacyCaseReference).isEmpty();
        assertThat(exceptionRecord.showEnvelopeCaseReference).isEqualTo("No");
        assertThat(exceptionRecord.showEnvelopeLegacyCaseReference).isEqualTo("No");
    }

    @Test
    public void mapEnvelope_sets_display_case_reference_fields_to_yes_when_envelope_case_reference_values_exist() {
        //given
        Envelope envelope = envelope("CASE_123", "LEGACY_CASE_123", Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR);

        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.envelopeCaseReference).isEqualTo("CASE_123");
        assertThat(exceptionRecord.envelopeLegacyCaseReference).isEqualTo("LEGACY_CASE_123");
        assertThat(exceptionRecord.showEnvelopeCaseReference).isEqualTo("Yes");
        assertThat(exceptionRecord.showEnvelopeLegacyCaseReference).isEqualTo("Yes");
    }

    @Test
    public void mapEnvelope_sets_surname_null_when_no_surname_data_in_ocr() {
        //given
        Envelope envelope = envelope("CASE_123", "LEGACY_CASE_123", Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR);
        // when
        ExceptionRecord exceptionRecord = mapper.mapEnvelope(envelope);

        // then
        assertThat(exceptionRecord.surname).isNull();
    }

    @Test
    public void mapEnvelope_sets_first_surname_when_multiple_surname_data_in_ocr() {
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
    public void mapEnvelope_sets_non_empty_surname_when_ocr_surname_data_empty() {

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
    public void mapEnvelope_sets_surname_with_first_matching_ocr_conf_when_multiple_conf_available() {
        //given
        given(serviceConfigItem.getSurnameOcrFieldNameList("FORM_TYPE"))
            .willReturn(asList("field_surname_not_found", "field_surname","fieldName1"));


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
    public void mapEnvelope_sets_surname_when_both_ocr_cof_available_by_using_first_surname_configuration() {
        //given
        given(serviceConfigItem.getSurnameOcrFieldNameList("FORM_TYPE"))
            .willReturn(asList("field_surname", "fieldName1"));

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
}
