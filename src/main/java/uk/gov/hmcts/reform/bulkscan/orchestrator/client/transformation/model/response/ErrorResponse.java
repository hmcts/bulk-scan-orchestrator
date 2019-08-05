package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ErrorResponse {

    public final List<String> errors;

    public ErrorResponse(
        @JsonProperty("errors") List<String> errors
    ) {
        this.errors = errors;
    }
}
