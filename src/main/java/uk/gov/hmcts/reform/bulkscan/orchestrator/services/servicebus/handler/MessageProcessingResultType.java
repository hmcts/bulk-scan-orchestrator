package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler;

public enum MessageProcessingResultType {
    SUCCESS,
    UNRECOVERABLE_FAILURE,
    POTENTIALLY_RECOVERABLE_FAILURE
}
