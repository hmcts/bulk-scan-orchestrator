package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.Instant;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.DatetimeHelper.toIso8601;

public class EnvelopeParserTest {

    private Envelope envelope;

    @Before
    public void setUp() {
        this.envelope = new Envelope(
            "975b339d-4531-4e32-8ebe-a7bc4650f33a",
            "case_ref_number",
            "po_box",
            "jurisdiction",
            "zip-file-test.zip",
            Instant.now(),
            Instant.now(),
            Classification.SUPPLEMENTARY_EVIDENCE,
            asList(
                new Document(
                    "doc1_file_name",
                    "doc1_control_number",
                    "doc1_type",
                    Instant.now(),
                    "doc1_url"
                ),
                new Document(
                    "doc2_file_name",
                    "doc2_control_number",
                    "doc2_type",
                    Instant.now(),
                    "doc2_url"
                )
            )
        );
    }

    @Test
    public void should_parse_valid_json() throws Exception {
        // given
        String json =
            new JSONObject()
                .put("id", envelope.id)
                .put("case_ref", envelope.caseRef)
                .put("po_box", envelope.poBox)
                .put("jurisdiction", envelope.jurisdiction)
                .put("zip_file_name", envelope.zipFileName)
                .put("delivery_date", envelope.deliveryDate)
                .put("opening_date", envelope.openingDate)
                .put("classification", envelope.classification.toString().toLowerCase())
                .put("documents", new JSONArray()
                    .put(toJson(envelope.getDocuments().get(0)))
                    .put(toJson(envelope.getDocuments().get(1)))
                )
                .toString();

        // when
        Envelope result = EnvelopeParser.parse(json.getBytes());

        // then
        assertThat(result).isEqualToComparingFieldByFieldRecursively(envelope);
    }

    @Test
    public void should_ignore_unrecognised_fields_in_json() throws Exception {
        // given
        String json =
            new JSONObject()
                .put("id", envelope.id)
                .put("case_ref", envelope.caseRef)
                .put("po_box", envelope.poBox)
                .put("jurisdiction", envelope.jurisdiction)
                .put("zip_file_name", envelope.zipFileName)
                .put("delivery_date", envelope.deliveryDate)
                .put("opening_date", envelope.openingDate)
                .put("classification", envelope.classification.toString().toLowerCase())
                .put("documents", new JSONArray()
                    .put(toJson(envelope.getDocuments().get(0)))
                    .put(toJson(envelope.getDocuments().get(1)))
                )
                .put("some_extra_ignored_field", "some_ignored_value")
                .toString();

        // when
        Envelope result = EnvelopeParser.parse(json.getBytes());

        // then
        assertThat(result).isEqualToComparingFieldByFieldRecursively(envelope);
    }

    @Test
    public void should_throw_an_exception_if_json_is_not_a_valid_envelope() throws Exception {
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
    public void should_throw_an_exception_if_invalid_json_is_passed() {

        String json = "gibberish";

        // when
        Throwable exc = catchThrowable(() -> EnvelopeParser.parse(json.getBytes()));

        // then
        assertThat(exc).isInstanceOf(InvalidMessageException.class);
    }

    @Test
    public void should_throw_an_exception_if_json_envelope_has_fields_missing() throws Exception {
        // given
        String jsonEnvelopeWithoutId =
            new JSONObject()
                .put("jurisdiction", "world")
                .put("doc_urls", new JSONArray(asList("a", "b")))
                .toString();

        // when
        Throwable exc = catchThrowable(() -> EnvelopeParser.parse(jsonEnvelopeWithoutId.getBytes()));

        // then
        assertThat(exc).isInstanceOf(InvalidMessageException.class);
    }

    @Test
    public void can_parse_example_json() {
        // given
        byte[] bytes = SampleData.exampleJsonAsBytes();

        // when
        Envelope anEnvelope = EnvelopeParser.parse(bytes);

        // then
        assertThat(anEnvelope.jurisdiction).isEqualTo("BULKSCAN");

    }

    @Test
    public void can_parse_sampleData_json() {
        // given
        byte[] json = SampleData.envelopeJson();

        // when
        Envelope anEnvelope = EnvelopeParser.parse(json);

        // then
        assertThat(anEnvelope.jurisdiction).isEqualTo("BULKSCAN");

    }

    private JSONObject toJson(Document doc) throws Exception {
        return new JSONObject()
            .put("file_name", doc.fileName)
            .put("control_number", doc.controlNumber)
            .put("type", doc.type)
            .put("scanned_at", toIso8601(doc.scannedAt))
            .put("url", doc.url);
    }
}
