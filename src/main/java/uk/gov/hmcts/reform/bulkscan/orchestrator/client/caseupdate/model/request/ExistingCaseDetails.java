package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ExistingCaseDetails {

    @JsonProperty("id")
    public final String id;

    @JsonProperty("case_type_id")
    public final String caseTypeId;

    @JsonProperty("case_data")
    public final Map<String, Object> data;

    public ExistingCaseDetails(
        String id,
        String caseTypeId,
        Map<String, Object> data
    ) {
        this.id = id;
        this.caseTypeId = caseTypeId;
        this.data = data;
    }
}
