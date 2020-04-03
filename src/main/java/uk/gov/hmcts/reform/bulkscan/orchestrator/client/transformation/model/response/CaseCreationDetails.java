package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import javax.validation.constraints.NotEmpty;

public class CaseCreationDetails {

    @NotEmpty
    public final String caseTypeId;

    @NotEmpty
    public final String eventId;

    @NotEmpty
    public final Map<String, Object> caseData;

    public CaseCreationDetails(
        @JsonProperty("case_type_id") String caseTypeId,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("case_data") Map<String, Object> caseData
    ) {
        this.caseTypeId = caseTypeId;
        this.eventId = eventId;
        this.caseData = caseData;
    }
}
