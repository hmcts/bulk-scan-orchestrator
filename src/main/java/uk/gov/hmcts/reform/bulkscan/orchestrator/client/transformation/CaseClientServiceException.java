package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;

public class CaseClientServiceException extends Exception {

    private static final long serialVersionUID = 8081182548244205380L;

    private final HttpStatus status;

    private final transient String response;

    public CaseClientServiceException(HttpStatusCodeException cause, String response) {
        super(cause);

        this.status = cause.getStatusCode();
        this.response = response;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getResponse() {
        return response;
    }

    public String getResponseRawBody() {
        return ((HttpStatusCodeException) getCause()).getResponseBodyAsString();
    }
}
