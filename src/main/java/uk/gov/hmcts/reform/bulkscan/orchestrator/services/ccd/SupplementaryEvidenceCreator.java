package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

@Service
public class SupplementaryEvidenceCreator {

    private static final Logger log = LoggerFactory.getLogger(SupplementaryEvidenceCreator.class);

    private static final String CASE_TYPE_ID = "Bulk_Scanned";
    private static final String EVENT_TYPE_ID = "attachScannedDocs";

    private final CcdAuthenticatorFactory authenticatorFactory;
    private final CoreCaseDataApi coreCaseDataApi;

    SupplementaryEvidenceCreator(
        CcdAuthenticatorFactory authenticatorFactory,
        CoreCaseDataApi coreCaseDataApi
    ) {
        this.authenticatorFactory = authenticatorFactory;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public void createSupplementaryEvidence(Envelope envelope) {
        log.info("Creating supplementary evidence for case {}", envelope.caseRef);

        CcdAuthenticator authenticator =
            authenticatorFactory.createForJurisdiction(envelope.jurisdiction);

        StartEventResponse startEventResponse =
            startEvent(authenticator, envelope.jurisdiction, envelope.caseRef);

        log.debug("Started {} event for case {}", startEventResponse.getEventId(), envelope.caseRef);

        CaseDataContent caseDataContent = prepareCaseDataContent(
            startEventResponse.getToken(),
            SupplementaryEvidenceMapper.fromEnvelope(envelope)
        );

        submitEvent(authenticator, envelope.jurisdiction, envelope.caseRef, caseDataContent);

        log.debug("Submitted {} event for case {}", startEventResponse.getEventId(), envelope.caseRef);
    }

    private CaseDataContent prepareCaseDataContent(
        String eventToken,
        SupplementaryEvidence supplementaryEvidence
    ) {
        return CaseDataContent.builder()
            .eventToken(eventToken)
            .event(Event.builder()
                .id(EVENT_TYPE_ID)
                .summary("Attach scanned documents")
                .build())
            .data(supplementaryEvidence)
            .build();
    }

    private StartEventResponse startEvent(
        CcdAuthenticator authenticator,
        String jurisdiction,
        String caseRef
    ) {
        return coreCaseDataApi.startEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            CASE_TYPE_ID,
            caseRef,
            EVENT_TYPE_ID
        );
    }

    private void submitEvent(
        CcdAuthenticator authenticator,
        String jurisdiction,
        String caseRef,
        CaseDataContent caseDataContent
    ) {
        coreCaseDataApi.submitEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            CASE_TYPE_ID,
            caseRef,
            true,
            caseDataContent
        );
    }
}
