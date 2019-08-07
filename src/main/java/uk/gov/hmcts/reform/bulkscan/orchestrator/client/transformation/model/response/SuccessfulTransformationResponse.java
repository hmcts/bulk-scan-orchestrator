package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SuccessfulTransformationResponse {

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
