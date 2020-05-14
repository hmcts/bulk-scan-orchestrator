package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

public class CaseUpdateDetails {

    public final String eventId;

    @NotNull
    public final Object caseData;

    public CaseUpdateDetails(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("case_data") Object caseData
    ) {
        this.eventId = eventId;
        this.caseData = caseData;
    }
}
