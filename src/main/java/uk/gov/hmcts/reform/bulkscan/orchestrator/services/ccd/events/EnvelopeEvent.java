package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

class EnvelopeEvent implements LoggableEvent<Envelope> {

    private final Envelope envelope;

    EnvelopeEvent(Envelope envelope) {
        this.envelope = envelope;
    }

    @Override
    public Envelope getEventSource() {
        return envelope;
    }

    @Override
    public String getJurisdiction() {
        return envelope.jurisdiction;
    }

    @Override
    public void logPublishStart(String eventClass) {
        log.info("Starting CCD Event of {} for case {}", eventClass, envelope.caseRef);
    }

    @Override
    public void logEventStart(String eventTypeId, String caseRef, String caseTypeId) {
        log.info(
            "Started CCD event of type {} for envelope with ID {}, file name {}, case ref {}, case type {}",
            eventTypeId,
            envelope.id,
            envelope.zipFileName,
            caseRef,
            caseTypeId
        );
    }

    @Override
    public void logSubmitEvent(String caseTypeId, String eventTypeId, Long submitEventResponseId) {
        log.info(
            "Created CCD case of type {} and event of type {}. Envelope ID: {}, file name: {}, case ID: {}",
            caseTypeId,
            eventTypeId,
            envelope.id,
            envelope.zipFileName,
            submitEventResponseId
        );
    }
}
