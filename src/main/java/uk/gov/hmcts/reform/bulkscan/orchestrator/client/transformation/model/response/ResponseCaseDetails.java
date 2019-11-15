package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResponseCaseDetails {

    public final String caseTypeId;

    public final String eventId;

    public final Object caseData;

    public ResponseCaseDetails(
        @JsonProperty("case_type_id") String caseTypeId,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("case_data") Object caseData
    ) {
        this.caseTypeId = caseTypeId;
        this.eventId = eventId;
        this.caseData = caseData;
    }
}
