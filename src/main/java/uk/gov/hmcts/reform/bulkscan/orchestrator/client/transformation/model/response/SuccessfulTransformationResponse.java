package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class SuccessfulTransformationResponse {

    @NotNull
    @Valid
    public final CaseCreationDetails caseCreationDetails;

    public final List<String> warnings;

    public SuccessfulTransformationResponse(
        @JsonProperty("case_creation_details") CaseCreationDetails caseCreationDetails,
        @JsonProperty("warnings") List<String> warnings
    ) {
        this.caseCreationDetails = caseCreationDetails;
        this.warnings = warnings;
    }
}
