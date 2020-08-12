package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

public enum CaseCreationResultType {
    CREATED_CASE,
    CASE_ALREADY_EXISTS,
    ABORTED_WITHOUT_FAILURE,
    UNRECOVERABLE_FAILURE,
    POTENTIALLY_RECOVERABLE_FAILURE
}
