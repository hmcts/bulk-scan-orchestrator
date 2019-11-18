package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.CaseTransformationException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.InvalidCaseDataException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.TransformationErrorResponse;

import java.io.IOException;

@Component
public class ServiceResponseParser {

    protected final ObjectMapper objectMapper;

    protected ServiceResponseParser(
        ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    public void tryParseResponseBodyAndThrow(HttpStatusCodeException exception) throws CaseTransformationException {
        try {
            TransformationErrorResponse errorResponse = objectMapper.readValue(
                exception.getResponseBodyAsByteArray(),
                TransformationErrorResponse.class
            );

            throw new InvalidCaseDataException(exception, errorResponse);
        } catch (IOException ioException) {
            throw new CaseTransformationException(exception, ioException.getMessage());
        }
    }
}
