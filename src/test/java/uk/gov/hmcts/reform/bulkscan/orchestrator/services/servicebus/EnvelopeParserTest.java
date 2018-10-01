package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
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
    public void setUp() throws Exception {
        this.envelope = new Envelope(
            "975b339d-4531-4e32-8ebe-a7bc4650f33a",
            "case_ref_number",
            "jurisdiction",
            "zip-file-test.zip",
            Classification.SUPPLEMENTARY_EVIDENCE,
            asList(
                new Document(
                    "file_name",
                    "control_number",
                    "type",
                    Instant.now(),
                    "url"
                ),
                new Document(
                    "file_name",
                    "control_number",
                    "type",
                    Instant.now(),
                    "url"
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
                .put("jurisdiction", envelope.jurisdiction)
                .put("zip_file_name", envelope.zipFileName)
                .put("classification", envelope.classification.toString().toLowerCase())
                .put("documents", new JSONArray()
                    .put(toJson(envelope.documents.get(0)))
                    .put(toJson(envelope.documents.get(1)))
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
                .put("jurisdiction", envelope.jurisdiction)
                .put("zip_file_name", envelope.zipFileName)
                .put("classification", envelope.classification.toString().toLowerCase())
                .put("documents", new JSONArray()
                    .put(toJson(envelope.documents.get(0)))
                    .put(toJson(envelope.documents.get(1)))
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

    private JSONObject toJson(Document doc) throws Exception {
        return new JSONObject()
            .put("file_name", doc.fileName)
            .put("control_number", doc.controlNumber)
            .put("type", doc.type)
            .put("scanned_at", toIso8601(doc.scannedAt))
            .put("url", doc.url);
    }
}
