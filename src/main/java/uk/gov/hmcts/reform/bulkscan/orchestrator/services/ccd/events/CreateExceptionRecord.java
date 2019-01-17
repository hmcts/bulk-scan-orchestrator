package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ModelMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

@Component
public class CreateExceptionRecord extends AbstractEventPublisher {

    public static final String CASE_TYPE = "ExceptionRecord";

    private final ModelMapper<? extends CaseData> mapper;

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
    CaseData mapEnvelopeToCaseDataObject(Envelope envelope) {
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
