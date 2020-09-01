package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.util.Map;

public class CaseUpdateDetails {

    public final String eventId;

    @NotNull
    public final Map<String, Object> caseData;

    public CaseUpdateDetails(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("case_data") Map<String, Object> caseData
    ) {
        this.eventId = eventId;
        this.caseData = caseData;
    }
}
