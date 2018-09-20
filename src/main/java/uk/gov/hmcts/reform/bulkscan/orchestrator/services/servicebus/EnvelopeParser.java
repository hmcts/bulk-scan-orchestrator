package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.io.IOException;

public class EnvelopeParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Envelope parse(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, Envelope.class);
        } catch (IOException exc) {
            throw new InvalidMessageException(exc);
        }
    }

    private EnvelopeParser() {
        // utility class
    }
}
