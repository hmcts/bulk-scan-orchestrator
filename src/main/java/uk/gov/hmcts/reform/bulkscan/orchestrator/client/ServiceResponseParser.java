package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;

import java.io.IOException;

@Component
public class ServiceResponseParser {

    protected final ObjectMapper objectMapper;

    protected ServiceResponseParser(
        ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    public void tryParseResponseBodyAndThrow(HttpStatusCodeException exception) throws CaseClientServiceException {
        try {
            ClientServiceErrorResponse errorResponse = objectMapper.readValue(
                exception.getResponseBodyAsByteArray(),
                ClientServiceErrorResponse.class
            );

            throw new InvalidCaseDataException(exception, errorResponse);
        } catch (IOException ioException) {
            throw new CaseClientServiceException(exception, ioException.getMessage());
        }
    }
}
