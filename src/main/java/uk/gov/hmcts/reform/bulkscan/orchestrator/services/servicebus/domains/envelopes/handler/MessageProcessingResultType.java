package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.handler;

public enum MessageProcessingResultType {
    SUCCESS,
    UNRECOVERABLE_FAILURE,
    POTENTIALLY_RECOVERABLE_FAILURE
}
