package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.io.IOException;

public class EnvelopeParser {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    public static Envelope parse(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, Envelope.class);
        } catch (IOException | NullPointerException exc) {
            throw new InvalidMessageException(exc);
        }
    }

    private EnvelopeParser() {
        // utility class
    }
}
