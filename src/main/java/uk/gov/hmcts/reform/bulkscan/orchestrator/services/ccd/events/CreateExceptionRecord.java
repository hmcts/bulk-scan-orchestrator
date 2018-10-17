package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

@Component
class CreateExceptionRecord extends AbstractEventPublisher {

    /**
     * Exception record does not present any existing case hence the creation of it.
     * @param envelope Original envelope
     * @return {@code null} as a case reference
     */
    @Override
    String getCaseRef(Envelope envelope) {
        return null;
    }

    @Override
    Object mapEnvelopeToCaseDataObject(Envelope envelope) {
        return ExceptionRecordMapper.fromEnvelope(envelope);
    }

    @Override
    String getEventTypeId() {
        return "createException";
    }

    @Override
    String getEventSummary() {
        return "Create an exception record";
    }

    CreateExceptionRecord() {
        // empty strategy constructor
    }
}
