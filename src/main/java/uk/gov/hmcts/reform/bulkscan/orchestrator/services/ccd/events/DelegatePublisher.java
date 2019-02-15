package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.checkerframework.checker.nullness.qual.NonNull;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

public class DelegatePublisher implements EventPublisher {

    private final EventPublisher publisher;
    private final String caseTypeId;

    DelegatePublisher(@NonNull EventPublisher publisher, @NonNull String caseTypeId) {
        this.publisher = publisher;
        this.caseTypeId = caseTypeId;
    }

    @Override
    public void publish(Envelope envelope) {
        publisher.publish(envelope, this.caseTypeId);
    }

    // only used in tests to verify which publisher is selected
    EventPublisher getDelegatedClass() {
        return publisher;
    }
}
