package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class InvalidExceptionRecordResponse {

    public final List<String> errors;
    public final List<String> warnings;

    public InvalidExceptionRecordResponse(
        @JsonProperty("errors") List<String> errors,
        @JsonProperty("warnings") List<String> warnings
    ) {
        this.errors = errors;
        this.warnings = warnings;
    }
}
