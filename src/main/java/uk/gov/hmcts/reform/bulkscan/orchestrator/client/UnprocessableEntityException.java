package uk.gov.hmcts.reform.bulkscan.orchestrator.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response.ClientServiceErrorResponse;

public class UnprocessableEntityException extends RuntimeException {

    private static final long serialVersionUID = -7795451777296451465L;

    private final HttpStatus status;

    private final transient ClientServiceErrorResponse response;

    public UnprocessableEntityException(
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
