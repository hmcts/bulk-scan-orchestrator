package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response.InvalidExceptionRecordResponse;

public class InvalidCaseDataException extends RuntimeException {

    private final HttpStatus status;

    private final transient InvalidExceptionRecordResponse response;

    public InvalidCaseDataException(
        HttpStatusCodeException cause,
        InvalidExceptionRecordResponse response
    ) {
        super(cause);
        this.status = cause.getStatusCode();
        this.response = response;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public InvalidExceptionRecordResponse getResponse() {
        return response;
    }

    public String getResponseRawBody() {
        return ((HttpStatusCodeException) getCause()).getResponseBodyAsString();
    }
}
