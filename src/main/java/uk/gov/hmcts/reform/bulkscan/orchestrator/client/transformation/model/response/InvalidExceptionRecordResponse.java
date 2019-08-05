package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class InvalidExceptionRecordResponse {

    private final List<String> errors;
    private final List<String> warnings;

    public InvalidExceptionRecordResponse(
        @JsonProperty("errors") List<String> errors,
        @JsonProperty("warnings") List<String> warnings
    ) {
        this.errors = errors;
        this.warnings = warnings;
    }
}
