package uk.gov.hmcts.reform.bulkscan.orchestrator.client.caseupdate.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CaseUpdateRequest {

    // commenting out as it won't compile. keeping so it is clear for us
    // @Deprecated(forRemoval = true, since = "Once all clients migrate to 'case_update_details' object")
    @JsonProperty("exception_record")
    public final ExceptionRecord exceptionRecord;

    @JsonProperty("is_automated_process")
    public final boolean isAutomatedProcess;

    @JsonProperty("case_update_details")
    public final CaseUpdateDetails caseUpdateDetails;

    @JsonProperty("case_details")
    public final ExistingCaseDetails caseDetails;

    public CaseUpdateRequest(
        ExceptionRecord exceptionRecord,
        boolean isAutomatedProcess,
        CaseUpdateDetails caseUpdateDetails,
        ExistingCaseDetails caseDetails
    ) {
        this.exceptionRecord = exceptionRecord;
        this.isAutomatedProcess = isAutomatedProcess;
        this.caseUpdateDetails = caseUpdateDetails;
        this.caseDetails = caseDetails;
    }
}
