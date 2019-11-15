package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.ClientServiceErrorResponse;

import java.io.IOException;

abstract class ServiceClient {

    protected final RestTemplate restTemplate;

    protected final ObjectMapper objectMapper;

    protected ServiceClient(
        RestTemplate restTemplate,
        ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    protected void tryParseResponseBodyAndThrow(HttpStatusCodeException exception) throws CaseClientServiceException {
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
