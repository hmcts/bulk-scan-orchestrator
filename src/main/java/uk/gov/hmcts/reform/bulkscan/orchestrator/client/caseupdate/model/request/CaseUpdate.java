package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;

public class CaseUpdate {
    @JsonProperty("exception_record")
    public final ExceptionRecord exceptionRecord;

    @JsonProperty("case_details")
    public final ExistingCaseDetails caseDetails;

    public CaseUpdate(
        @JsonProperty("exception_record") ExceptionRecord exceptionRecord,
        @JsonProperty("case_details") ExistingCaseDetails caseDetails
    ) {
        this.exceptionRecord = exceptionRecord;
        this.caseDetails = caseDetails;
    }
}
