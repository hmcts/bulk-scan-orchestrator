package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class CaseCreationDetails {

    @NotEmpty
    public final String caseTypeId;

    @NotEmpty
    public final String eventId;

    @NotNull
    public final Object caseData;

    public CaseCreationDetails(
        @JsonProperty("case_type_id") String caseTypeId,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("case_data") Object caseData
    ) {
        this.caseTypeId = caseTypeId;
        this.eventId = eventId;
        this.caseData = caseData;
    }
}
