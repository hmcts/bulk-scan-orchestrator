package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

public interface EventPublisher {

    String BULK_SCANNED = "Bulk_Scanned";
    String EXCEPTION_RECORD = "ExceptionRecord";

    void publish(Envelope envelope);
}
