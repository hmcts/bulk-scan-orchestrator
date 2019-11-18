package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CaseUpdateDetails {

    public final Object caseData;

    public CaseUpdateDetails(
        @JsonProperty("case_data") Object caseData
    ) {
        this.caseData = caseData;
    }
}
