package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.google.common.collect.ImmutableList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.OcrDataField;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.TimeZone;

import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.DatetimeHelper.toIso8601;

class EnvelopeParserTest {

    private Envelope envelope;
    private final Instant scannedAt = ZonedDateTime.parse("2018-10-01T00:00:00.100Z").toInstant();
    private final Instant deliveryDate = ZonedDateTime.parse("2018-10-01T00:00:00.100Z").toInstant();

    @BeforeAll
    static void init() {
        TimeZone.setDefault(TimeZone.getTimeZone(UTC));
    }

    @BeforeEach
    void setUp() {
        this.envelope = new Envelope(
            "975b339d-4531-4e32-8ebe-a7bc4650f33a",
            "case_ref_number",
            "case_legacy_id",
            "po_box",
            "jurisdiction",
            "container",
            "zip-file-test.zip",
            "form_type",
            Instant.now(),
            Instant.now(),
            Classification.SUPPLEMENTARY_EVIDENCE,
            asList(
                new Document(
                    "doc1_file_name",
                    "doc1_control_number",
                    "doc1_type",
                    "doc1_subtype",
                    scannedAt,
                    "doc1uuid",
                    deliveryDate
                ),
                new Document(
                    "doc2_file_name",
                    "doc2_control_number",
                    "doc2_type",
                    null,
                    scannedAt,
                    "doc2uuid",
                    deliveryDate
                )
            ),
            ImmutableList.of(
                new Payment("dcn1")
            ),
            ImmutableList.of(
                new OcrDataField("key1", "value1"),
                new OcrDataField("key2", "value2"),
                new OcrDataField("key0", "value0")
            ),
            ImmutableList.of("warning 1")
        );
    }

    @Test
    void should_parse_valid_json() throws Exception {
        // given
        String json =
            new JSONObject()
                .put("id", envelope.id)
                .put("case_ref", envelope.caseRef)
                .put("previous_service_case_ref", envelope.legacyCaseRef)
                .put("po_box", envelope.poBox)
                .put("jurisdiction", envelope.jurisdiction)
                .put("container", envelope.container)
                .put("zip_file_name", envelope.zipFileName)
                .put("form_type", envelope.formType)
                .put("delivery_date", envelope.deliveryDate)
                .put("opening_date", envelope.openingDate)
                .put("classification", envelope.classification.toString().toLowerCase())
                .put("documents", new JSONArray()
                    .put(toJson(envelope.documents.get(0)))
                    .put(toJson(envelope.documents.get(1)))
                )
                .put("payments", toPaymentsJson(envelope.payments))
                .put("ocr_data", toOcrJson(envelope.ocrData))
                .put("ocr_data_validation_warnings", new JSONArray(envelope.ocrDataValidationWarnings))
                .toString();

        // when
        Envelope result = EnvelopeParser.parse(json.getBytes());

        // then
        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(envelope);

        assertThat(result.documents)
            .extracting(
                doc -> tuple(
                    doc.fileName,
                    doc.controlNumber,
                    doc.type,
                    doc.subtype,
                    doc.scannedAt,
                    doc.uuid,
                    doc.deliveryDate
                )
            )
            .containsOnly(
                tuple(
                    "doc1_file_name",
                    "doc1_control_number",
                    "doc1_type",
                    "doc1_subtype",
                    scannedAt,
                    "doc1uuid",
                    deliveryDate
                ),
                tuple(
                    "doc2_file_name",
                    "doc2_control_number",
                    "doc2_type",
                    null,
                    scannedAt,
                    "doc2uuid",
                    deliveryDate
                )
            );
    }

    @Test
    void should_ignore_unrecognised_fields_in_json() throws Exception {
        // given
        String json =
            new JSONObject()
                .put("id", envelope.id)
                .put("case_ref", envelope.caseRef)
                .put("previous_service_case_ref", envelope.legacyCaseRef)
                .put("po_box", envelope.poBox)
                .put("jurisdiction", envelope.jurisdiction)
                .put("container", envelope.container)
                .put("zip_file_name", envelope.zipFileName)
                .put("form_type", envelope.formType)
                .put("delivery_date", envelope.deliveryDate)
                .put("opening_date", envelope.openingDate)
                .put("classification", envelope.classification.toString().toLowerCase())
                .put("documents", new JSONArray()
                    .put(toJson(envelope.documents.get(0)))
                    .put(toJson(envelope.documents.get(1)))
                )
                .put("some_extra_ignored_field", "some_ignored_value")
                .put("payments", toPaymentsJson(envelope.payments))
                .put("ocr_data", toOcrJson(envelope.ocrData))
                .put("ocr_data_validation_warnings", new JSONArray(envelope.ocrDataValidationWarnings))
                .toString();

        // when
        Envelope result = EnvelopeParser.parse(json.getBytes());

        // then
        assertThat(result)
            .usingRecursiveComparison()
            .isEqualTo(envelope);
    }

    @Test
    void should_throw_an_exception_if_json_is_not_a_valid_envelope() throws Exception {
        String json =
            new JSONObject()
                .put("hello", "world")
                .toString();

        // when
        Throwable exc = catchThrowable(() -> EnvelopeParser.parse(json.getBytes()));

        // then
        assertThat(exc).isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void should_throw_an_exception_if_invalid_json_is_passed() {

        String json = "gibberish";

        // when
        Throwable exc = catchThrowable(() -> EnvelopeParser.parse(json.getBytes()));

        // then
        assertThat(exc).isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void should_throw_an_exception_if_json_envelope_has_fields_missing() throws Exception {
        // given
        String jsonEnvelopeWithoutId =
            new JSONObject()
                .put("jurisdiction", "world")
                .put("doc_uuids", new JSONArray(asList("doc1uuid", "doc2uuid")))
                .toString();

        // when
        Throwable exc = catchThrowable(() -> EnvelopeParser.parse(jsonEnvelopeWithoutId.getBytes()));

        // then
        assertThat(exc).isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void can_parse_example_json() {
        // given
        byte[] bytes = SampleData.exampleJsonAsBytes();

        // when
        Envelope anEnvelope = EnvelopeParser.parse(bytes);

        // then
        assertThat(anEnvelope.jurisdiction).isEqualTo("BULKSCAN");
        assertThat(anEnvelope.container).isEqualTo("container");

    }

    @Test
    void can_parse_sampleData_json() {
        // given
        byte[] json = SampleData.envelopeJson();

        // when
        Envelope anEnvelope = EnvelopeParser.parse(json);

        // then
        assertThat(anEnvelope.jurisdiction).isEqualTo("BULKSCAN");
        assertThat(anEnvelope.container).isEqualTo("bulkscan");

    }

    private JSONObject toJson(Document doc) throws Exception {
        return new JSONObject()
            .put("file_name", doc.fileName)
            .put("control_number", doc.controlNumber)
            .put("type", doc.type)
            .put("subtype", doc.subtype)
            .put("scanned_at", toIso8601(doc.scannedAt))
            .put("uuid", doc.uuid)
            .put("delivery_date", toIso8601(doc.deliveryDate));
    }

    private JSONArray toPaymentsJson(List<Payment> payments) throws JSONException {
        JSONArray paymentsJson = new JSONArray();
        JSONObject paymentEntry;

        for (Payment payment : payments) {
            paymentEntry = new JSONObject();
            paymentEntry.put("document_control_number", payment.documentControlNumber);
            paymentsJson.put(paymentEntry);
        }
        return paymentsJson;
    }

    private JSONArray toOcrJson(List<OcrDataField> ocrDataFields) throws JSONException {
        JSONArray ocrJson = new JSONArray();
        JSONObject ocrDataEntry;

        for (OcrDataField ocrField : ocrDataFields) {
            ocrDataEntry = new JSONObject();
            ocrDataEntry.put("metadata_field_name", ocrField.name);
            ocrDataEntry.put("metadata_field_value", ocrField.value);
            ocrJson.put(ocrDataEntry);
        }
        return ocrJson;
    }
}
