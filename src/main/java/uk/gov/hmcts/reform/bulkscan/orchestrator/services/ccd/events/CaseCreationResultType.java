package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

public enum CaseCreationResultType {
    CASE_CREATED,
    CASE_ALREADY_EXISTS,
    ABORTED_WITHOUT_FAILURE,
    UNRECOVERABLE_FAILURE,
    POTENTIALLY_RECOVERABLE_FAILURE
}
