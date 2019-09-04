package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.TransformationErrorResponse;

public class InvalidCaseDataException extends RuntimeException {

    private static final long serialVersionUID = -8700417115686776610L;

    private final HttpStatus status;

    private final transient TransformationErrorResponse response;

    public InvalidCaseDataException(
        HttpStatusCodeException cause,
        TransformationErrorResponse response
    ) {
        super(cause);
        this.status = cause.getStatusCode();
        this.response = response;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public TransformationErrorResponse getResponse() {
        return response;
    }

    public String getResponseRawBody() {
        return ((HttpStatusCodeException) getCause()).getResponseBodyAsString();
    }
}
