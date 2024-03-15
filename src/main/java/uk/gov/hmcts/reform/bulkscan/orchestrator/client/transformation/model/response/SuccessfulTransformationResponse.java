package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class SuccessfulTransformationResponse {

    @NotNull
    @Valid
    public final CaseCreationDetails caseCreationDetails;

    public final List<String> warnings;

    public final Map<String, Map<String, Object>> supplementaryData;

    public SuccessfulTransformationResponse(
        @JsonProperty("case_creation_details") CaseCreationDetails caseCreationDetails,
        @JsonProperty("warnings") List<String> warnings,
        @JsonProperty("supplementary_data") Map<String, Map<String, Object>> supplementaryData
    ) {
        this.caseCreationDetails = caseCreationDetails;
        this.warnings = warnings;
        this.supplementaryData = supplementaryData;
    }
}
