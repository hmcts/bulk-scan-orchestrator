package uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ClientServiceErrorResponse {

    public final List<String> errors;
    public final List<String> warnings;

    public ClientServiceErrorResponse(
        @JsonProperty("errors") List<String> errors,
        @JsonProperty("warnings") List<String> warnings
    ) {
        this.errors = errors;
        this.warnings = warnings;
    }
}
