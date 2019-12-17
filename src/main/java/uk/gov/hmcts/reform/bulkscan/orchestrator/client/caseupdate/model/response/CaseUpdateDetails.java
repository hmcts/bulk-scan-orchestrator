package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CaseUpdateDetails {

    @JsonProperty("event_id")
    public final String eventId;

    @JsonProperty("case_data")
    public final Object caseData;

    public CaseUpdateDetails(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("case_data") Object caseData
    ) {
        this.eventId = eventId;
        this.caseData = caseData;
    }
}
