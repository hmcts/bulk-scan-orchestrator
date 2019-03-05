package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

@Component
public class CreateExceptionRecord extends AbstractEventPublisher<Envelope> {

    private static final Logger log = LoggerFactory.getLogger(CreateExceptionRecord.class);

    public static final String CASE_TYPE = "ExceptionRecord";
    public static final String EVENT_TYPE_ID = "createException";
    public static final String EVENT_SUMMARY = "Create an exception record";

    private final ExceptionRecordMapper mapper;

    CreateExceptionRecord(ExceptionRecordMapper mapper) {
        this.mapper = mapper;
    }

    public void publish(Envelope envelope) {
        log.info("Creating exception record for envelope {}", envelope.id);
        publish(envelope, envelope.jurisdiction + "_" + CASE_TYPE, EVENT_TYPE_ID, EVENT_SUMMARY);
    }

    /**
     * Exception record does not present any existing case hence the creation of it.
     * @param eventSource Original envelope
     * @return {@code null} as a case reference
     */
    @Override
    String getCaseReference(Envelope eventSource) {
        return null;
    }

    @Override
    CaseData buildCaseData(StartEventResponse eventResponse, Envelope eventSource) {
        return mapper.mapEnvelope(eventSource);
    }
}
