package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.SupplementaryEvidence;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.mappers.SupplementaryEvidenceMapper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
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
        log.info("Case id: " + envelope.caseRef);

        CcdAuthenticator info = authenticatorFactory.createForJurisdiction(envelope.jurisdiction);

        StartEventResponse startEventResponse = startEvent(info, envelope);

        CaseDataContent caseDataContent = prepareCaseDataContent(
            startEventResponse.getToken(),
            SupplementaryEvidenceMapper.fromEnvelope(envelope)
        );

        log.info("Event id: " + startEventResponse.getEventId());
        log.info("Event token: " + startEventResponse.getToken());
        log.info("Case data: " + startEventResponse.getCaseDetails().getData().toString());
        log.info("Case id: " + startEventResponse.getCaseDetails().getId());
        log.info("Case type id: " + startEventResponse.getCaseDetails().getCaseTypeId());
        log.info("Case state: " + startEventResponse.getCaseDetails().getState());
        log.info("Document count: " + envelope.documents.size());

        submitEvent(info, envelope, caseDataContent);
    }

    private CaseDataContent prepareCaseDataContent(String eventToken, SupplementaryEvidence supplementaryEvidence) {
       return CaseDataContent.builder()
           .eventToken(eventToken)
           .event(Event.builder()
               .id(EVENT_TYPE_ID)
               .summary("Attach scanned documents")
               .description("Attach scanned documents")
               .build())
           .data(supplementaryEvidence)
           .build();
    }

    private StartEventResponse startEvent(CcdAuthenticator authenticator, Envelope envelope) {
        return coreCaseDataApi.startEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.userDetails.getId(),
            envelope.jurisdiction,
            CASE_TYPE_ID,
            envelope.caseRef,
            EVENT_TYPE_ID
        );
    }

    private void submitEvent(CcdAuthenticator authenticator, Envelope envelope, CaseDataContent caseDataContent) {
        log.info("Case data content: " + caseDataContent.getData().toString());

        CaseDetails caseDetails = coreCaseDataApi.submitEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.userDetails.getId(),
            envelope.jurisdiction,
            CASE_TYPE_ID,
            envelope.caseRef,
            true,
            caseDataContent
        );

        log.info("New case details: " + caseDetails.getData().toString());
    }
}
