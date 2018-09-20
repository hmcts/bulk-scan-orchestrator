package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class EnvelopeParserTest {

    @Test
    public void should_parse_valid_json() throws Exception {
        // given
        Envelope envelope = new Envelope(
            "975b339d-4531-4e32-8ebe-a7bc4650f33a",
            "case_ref_number",
            "jurisdiction",
            asList("a", "b", "c")
        );

        String json =
            new JSONObject()
                .put("id", envelope.id)
                .put("case_ref", envelope.caseRef)
                .put("jurisdiction", envelope.jurisdiction)
                .put("doc_urls", new JSONArray(envelope.docUrls))
                .toString();

        // when
        Envelope result = EnvelopeParser.parse(json.getBytes());

        // then
        assertThat(result).isEqualToComparingFieldByField(envelope);
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
    public void should_throw_an_exception_if_invalid_json_is_passed() throws Exception {

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
}
