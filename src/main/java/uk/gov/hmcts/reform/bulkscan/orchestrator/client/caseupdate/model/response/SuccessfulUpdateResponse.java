package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class SuccessfulUpdateResponse {

    @Valid
    @NotNull
    public final CaseUpdateDetails caseDetails;

    public final List<String> warnings;

    public SuccessfulUpdateResponse(
        @JsonProperty("case_update_details") CaseUpdateDetails caseDetails,
        @JsonProperty("warnings") List<String> warnings
    ) {
        this.caseDetails = caseDetails;
        this.warnings = warnings;
    }
}
