package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.ExceptionRecordMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;

import java.util.List;
import java.util.Locale;

@Component
public class CreateExceptionRecord {

    private static final Logger log = LoggerFactory.getLogger(CreateExceptionRecord.class);

    public static final String CASE_TYPE = "ExceptionRecord";
    private static final String EVENT_TYPE_ID = "createException";
    private static final String EVENT_SUMMARY = "Create an exception record";

    private final ExceptionRecordMapper mapper;
    private final CcdApi ccdApi;

    public CreateExceptionRecord(
        ExceptionRecordMapper mapper,
        CcdApi ccdApi
    ) {
        this.mapper = mapper;
        this.ccdApi = ccdApi;
    }

    /**
     * Creates an exception record from given envelope, unless an exception record
     * already exists for this envelope.
     *
     * @return ccdReference of the created or already existing exception record
     */
    public Long tryCreateFrom(Envelope envelope) {
        log.info("Checking for existing exception records for envelope {}", envelope.id);

        List<Long> existingExceptionRecords =
            ccdApi.getExceptionRecordRefsByEnvelopeId(envelope.id, envelope.container);

        if (!existingExceptionRecords.isEmpty()) {
            log.error(
                "Creating of exception record aborted - exception records already exist for envelope {}: [{}]",
                envelope.id,
                StringUtils.join(existingExceptionRecords, ",")
            );

            return existingExceptionRecords.get(0);
        } else {
            return createExceptionRecord(envelope);
        }
    }

    private Long createExceptionRecord(Envelope envelope) {
        String loggingContext = String.format(
            "Envelope ID: %s, file name: %s, service: %s",
            envelope.id,
            envelope.zipFileName,
            envelope.container
        );
        log.info("Creating exception record. {}", loggingContext);

        CcdAuthenticator authenticator = ccdApi.authenticateJurisdiction(envelope.jurisdiction);
        String caseTypeId = envelope.container.toUpperCase(Locale.getDefault()) + "_" + CASE_TYPE;

        CaseDetails caseDetails = ccdApi.createExceptionRecord(
            authenticator,
            envelope.jurisdiction,
            caseTypeId,
            EVENT_TYPE_ID,
            startEventResponse -> buildCaseDataContent(envelope, startEventResponse.getToken()),
            loggingContext
        );

        log.info(
            "Created exception record. Case ID: {}, case type: {}. {}",
            caseDetails.getId(),
            caseTypeId,
            loggingContext
        );

        return caseDetails.getId();
    }

    private CaseDataContent buildCaseDataContent(Envelope envelope, String startEventResponseToken) {
        return CaseDataContent.builder()
            .eventToken(startEventResponseToken)
            .event(Event.builder()
                .id(EVENT_TYPE_ID)
                .summary(EVENT_SUMMARY)
                .build())
            .data(mapper.mapEnvelope(envelope))
            .build();
    }
}
