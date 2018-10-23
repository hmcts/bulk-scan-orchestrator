package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ModelMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseTypeId;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

@Component
class CreateExceptionRecord extends AbstractEventPublisher {

    private final ModelMapper<? extends CaseData> mapper;

    CreateExceptionRecord(ExceptionRecordMapper mapper) {
        super(CaseTypeId.CASE_TYPE_EXCEPTION_RECORD);
        this.mapper = mapper;
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
