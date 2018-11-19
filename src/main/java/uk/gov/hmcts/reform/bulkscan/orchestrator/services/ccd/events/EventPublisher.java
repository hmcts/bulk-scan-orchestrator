package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.apache.commons.lang.NotImplementedException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

public interface EventPublisher {

    default void publish(Envelope envelope) {
        throw new NotImplementedException();
    }

    default void publish(Envelope envelope, String caseTypeId) {
        throw new NotImplementedException();
    }
}
