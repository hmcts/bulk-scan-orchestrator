package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

public interface EventPublisher {

    String getCaseTypeIdForEvent();

    void publish(Envelope envelope);
}
