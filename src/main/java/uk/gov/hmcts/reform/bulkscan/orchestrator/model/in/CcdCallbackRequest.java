package uk.gov.hmcts.reform.bulkscan.orchestrator.model.in;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

public class CcdCallbackRequest {

    private final String eventId;
    private final CaseDetails caseDetails;
    private final boolean ignoreWarnings;

    public CcdCallbackRequest(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("case_details") CaseDetails caseDetails,
        @JsonProperty("ignore_warning") boolean ignoreWarnings
    ) {
        this.eventId = eventId;
        this.caseDetails = caseDetails;
        this.ignoreWarnings = ignoreWarnings;
    }

    public String getEventId() {
        return eventId;
    }

    public CaseDetails getCaseDetails() {
        return caseDetails;
    }

    public boolean isIgnoreWarnings() {
        return ignoreWarnings;
    }
}
