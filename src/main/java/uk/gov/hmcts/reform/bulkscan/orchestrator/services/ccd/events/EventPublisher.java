package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

public interface EventPublisher {

    // temporary adding custom case types until ccd api is updated
    // at the moment BULKSCAN jurisdiction is able to use following types anyway
    String CASE_TYPE_BULK_SCANNED = "Bulk_Scanned";
    String CASE_TYPE_EXCEPTION_RECORD = "ExceptionRecord";

    void publish(Envelope envelope);
}
