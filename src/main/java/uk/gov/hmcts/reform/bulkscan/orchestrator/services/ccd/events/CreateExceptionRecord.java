package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

@Component
class CreateExceptionRecord extends AbstractEventPublisher {

    static final String CASE_TYPE = "ExceptionRecord";

    private final ExceptionRecordMapper mapper;

    CreateExceptionRecord(ExceptionRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void publish(Envelope envelope) {
        publish(envelope, envelope.jurisdiction + "_" + CASE_TYPE);
    }

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
    CaseData buildCaseData(StartEventResponse eventResponse, Envelope envelope) {
        return mapper.mapEnvelope(envelope);
    }

    @Override
    String getEventTypeId() {
        return "createException";
    }

    @Override
    String getEventSummary() {
        return "Create an exception record";
    }
}
