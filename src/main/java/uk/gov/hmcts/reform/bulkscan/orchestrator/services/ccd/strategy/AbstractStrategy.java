package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.function.Function;

abstract class AbstractStrategy implements Strategy {

    private static final String CASE_TYPE_ID = "Bulk_Scanned";

    private static final Logger log = LoggerFactory.getLogger(AbstractStrategy.class);

    private Function<String, CcdAuthenticator> authenticatorProvider;

    private CoreCaseDataApi ccdApi;

    protected void setAuthenticatorProvider(Function<String, CcdAuthenticator> authenticatorProvider) {
        this.authenticatorProvider = authenticatorProvider;
    }

    protected void setCcdApi(CoreCaseDataApi ccdApi) {
        this.ccdApi = ccdApi;
    }

    @Override
    public void execute(Envelope envelope) {
        String caseRef = envelope.caseRef;
        String jurisdiction = envelope.jurisdiction;

        logCaseCreationEntry(caseRef);

        CcdAuthenticator authenticator = authenticateJurisdiction(jurisdiction);
        StartEventResponse eventResponse = startEvent(authenticator, jurisdiction, caseRef);
        CaseDataContent caseDataContent = buildCaseDataContent(
            eventResponse.getToken(),
            mapEnvelopeToCaseDataObject(envelope)
        );
        submitEvent(authenticator, jurisdiction, caseRef, caseDataContent);
    }

    // region - execution steps

    private void logCaseCreationEntry(String caseRef) {
        log.info("Creating {} for case {}", getClass().getSimpleName(), caseRef);
    }

    private CcdAuthenticator authenticateJurisdiction(String jurisdiction) {
        return authenticatorProvider.apply(jurisdiction);
    }

    private StartEventResponse startEvent(
        CcdAuthenticator authenticator,
        String jurisdiction,
        String caseRef
    ) {
        return ccdApi.startEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            CASE_TYPE_ID,
            caseRef,
            getEventTypeId()
        );
    }

    // todo perhaps some generic interface for these data objects?
    abstract Object mapEnvelopeToCaseDataObject(Envelope envelope);

    private CaseDataContent buildCaseDataContent(
        String eventToken,
        Object caseDataObject
    ) {
        return CaseDataContent.builder()
            .eventToken(eventToken)
            .event(Event.builder()
                .id(getEventTypeId())
                .summary(getEventSummary())
                .build())
            .data(caseDataObject)
            .build();
    }

    private void submitEvent(
        CcdAuthenticator authenticator,
        String jurisdiction,
        String caseRef,
        CaseDataContent caseDataContent
    ) {
        ccdApi.submitEventForCaseWorker(
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

    // end region - execution steps

    abstract String getEventTypeId();

    abstract String getEventSummary();
}
