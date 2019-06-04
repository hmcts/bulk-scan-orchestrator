package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

@Component
public class CreateExceptionRecord extends AbstractEventPublisher {

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

        CcdAuthenticator authenticator = ccdApi.authenticateJurisdiction(envelope.jurisdiction);
        String caseTypeId = envelope.container.toUpperCase() + "_" + CASE_TYPE;

        StartEventResponse startEventResponse = ccdApi.startEvent(
            authenticator,
            envelope.jurisdiction,
            caseTypeId,
            null,
            EVENT_TYPE_ID
        );

        CaseDetails caseDetails = ccdApi.submitEvent(
            authenticator,
            envelope.jurisdiction,
            caseTypeId,
            null,
            CaseDataContent.builder()
                .eventToken(startEventResponse.getToken())
                .event(Event.builder()
                    .id(EVENT_TYPE_ID)
                    .summary(EVENT_SUMMARY)
                    .build())
                .data(mapper.mapEnvelope(envelope))
                .build()
        );

        log.info(
            "Created Exception Record. Envelope ID: {}, file name: {}, case ID: {}, case type: {}",
            envelope.id,
            envelope.zipFileName,
            caseDetails.getId(),
            caseTypeId
        );
    }
}
