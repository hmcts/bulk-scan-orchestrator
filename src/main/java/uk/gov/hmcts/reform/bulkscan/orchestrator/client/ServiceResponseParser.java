package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;

import java.io.IOException;

@Component
public class ServiceResponseParser {

    private final ObjectMapper objectMapper;

    protected ServiceResponseParser(
        ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    public ClientServiceErrorResponse parseResponseBody(HttpStatusCodeException exception) {
        try {
            ClientServiceErrorResponse errorResponse = objectMapper.readValue(
                exception.getResponseBodyAsByteArray(),
                ClientServiceErrorResponse.class
            );

            return errorResponse;
        } catch (IOException ioException) {
            throw new CallbackException("Failed to parse response", ioException);
        }
    }
}
