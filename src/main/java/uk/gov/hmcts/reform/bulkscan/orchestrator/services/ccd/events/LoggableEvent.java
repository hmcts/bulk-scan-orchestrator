package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

interface LoggableEvent<T> {

    Logger log = LoggerFactory.getLogger(LoggableEvent.class);

    static <V> LoggableEvent getInstance(V eventSource) {
        if (eventSource instanceof Envelope) {
            return new EnvelopeEvent((Envelope) eventSource);
        }

        throw new UnknownEventException(
            "Could not understand event source of " + eventSource.getClass().getCanonicalName()
        );
    }

    T getEventSource();

    String getJurisdiction();

    void logPublishStart(String eventClass);

    void logEventStart(String eventTypeId, String caseRef, String caseTypeId);

    void logSubmitEvent(String caseTypeId, String eventTypeId, Long submitEventResponseId);
}
