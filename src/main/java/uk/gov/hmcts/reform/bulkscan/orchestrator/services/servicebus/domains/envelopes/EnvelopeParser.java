package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.io.IOException;

public class EnvelopeParser {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    public static Envelope parse(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, Envelope.class);
        } catch (IOException exc) {
            throw new InvalidMessageException(exc);
        }
    }

    public static Envelope parse(String json) {
        return parse(json.getBytes());
    }

    private EnvelopeParser() {
        // utility class
    }
}
