package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CaseUpdate {
    @JsonProperty("exception_record")
    public final ExceptionRecord exceptionRecord;

    @JsonProperty("case_details")
    public final ExistingCaseDetails caseDetails;

    public CaseUpdate(
        ExceptionRecord exceptionRecord,
        ExistingCaseDetails caseDetails
    ) {
        this.exceptionRecord = exceptionRecord;
        this.caseDetails = caseDetails;
    }
}
