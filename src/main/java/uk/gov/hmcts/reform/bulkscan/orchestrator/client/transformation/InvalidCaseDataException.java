package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;

public class InvalidCaseDataException extends RuntimeException {

    private static final long serialVersionUID = -8700417115686776610L;

    private final HttpStatus status;

    private final transient ClientServiceErrorResponse response;

    public InvalidCaseDataException(
        HttpStatusCodeException cause,
        ClientServiceErrorResponse response
    ) {
        super(cause);
        this.status = cause.getStatusCode();
        this.response = response;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ClientServiceErrorResponse getResponse() {
        return response;
    }

    public String getResponseRawBody() {
        return ((HttpStatusCodeException) getCause()).getResponseBodyAsString();
    }
}
