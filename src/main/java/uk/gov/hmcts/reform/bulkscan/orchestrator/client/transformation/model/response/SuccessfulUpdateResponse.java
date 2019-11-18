package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SuccessfulUpdateResponse {

    public final CaseUpdateDetails caseDetails;

    public final List<String> warnings;

    public SuccessfulUpdateResponse(
        @JsonProperty("case_details") CaseUpdateDetails caseDetails,
        @JsonProperty("warnings") List<String> warnings
    ) {
        this.caseDetails = caseDetails;
        this.warnings = warnings;
    }
}
