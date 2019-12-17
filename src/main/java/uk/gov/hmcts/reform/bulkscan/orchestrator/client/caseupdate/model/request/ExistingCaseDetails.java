package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ExistingCaseDetails {
    @JsonProperty("case_type_id")
    public final String caseTypeId;

    @JsonProperty("case_data")
    public final Map<String, Object> data;

    public ExistingCaseDetails(
        @JsonProperty("case_type_id") String caseTypeId,
        @JsonProperty("case_data") Map<String, Object> data
    ) {
        this.caseTypeId = caseTypeId;
        this.data = data;
    }
}
