package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

public class AutoCaseUpdateResult {

    public final AutoCaseUpdateResultType type;
    public final Long caseId;

    public AutoCaseUpdateResult(
        AutoCaseUpdateResultType type,
        Long caseId
    ) {
        this.type = type;
        this.caseId = caseId;
    }
}
