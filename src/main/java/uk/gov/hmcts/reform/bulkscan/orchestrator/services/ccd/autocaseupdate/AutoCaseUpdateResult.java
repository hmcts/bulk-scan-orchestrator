package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate;

public enum AutoCaseUpdateResult {
    OK,        // case was updated successfully
    ERROR,     // an error occurred when updating a case
    ABANDONED, // conditions for updating a case were not met, there was no attempt to update a case
}
