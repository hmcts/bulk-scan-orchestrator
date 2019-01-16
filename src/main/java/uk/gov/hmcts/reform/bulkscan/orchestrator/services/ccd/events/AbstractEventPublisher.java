package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

/**
 * Implementation of strategy.
 * Any strategy is invoking steps as follows:
 * <ul>
 *     <li>jurisdiction authentication</li>
 *     <li>start the event</li>
 *     <li>create data content accordingly</li>
 *     <li>submit the event</li>
 * </ul>
 * Any publisher will have to be implemented as an example:
 * <pre>{@code
 * @Component
 * class PublisherName extends AbstractEventPublisher {
 *
 *     PublisherName() {
 *         // any extra autowiring needed
 *     }
 *
 *     @Override
 *     Object mapEnvelopeToCaseDataObject(Envelope envelope) {
 *         return null; // implement this
 *     }
 *
 *     @Override
 *     String getEventTypeId() {
 *         return ""
 *     }
 *
 *     @Override
 *     String getEventSummary() {
 *         return ""
 *     }
 * }}</pre>
 * <p/>
 * No need to make any public access as everything will be injected in {@link EventPublisherContainer}:
 * <pre>{@code
 * private final EventPublisher somePublisher;}</pre>
 * <p/>
 * Then include each publisher in {@link EventPublisherContainer}
 */
abstract class AbstractEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AbstractEventPublisher.class);

    @Autowired
    private CoreCaseDataApi ccdApi;

    @Autowired
    private CcdAuthenticatorFactory authenticatorFactory;

    AbstractEventPublisher() {
    }

    @Override
    public void publish(Envelope envelope, String caseTypeId) {
        String caseRef = envelope.caseRef;

        logCaseCreationEntry(caseRef);

        CcdAuthenticator authenticator = authenticateJurisdiction(envelope.jurisdiction);
        StartEventResponse eventResponse = startEvent(authenticator, envelope, caseTypeId);
        CaseDataContent caseDataContent = buildCaseDataContent(eventResponse, envelope);
        submitEvent(authenticator, envelope, caseDataContent, caseTypeId);
    }

    // region - execution steps

    private void logCaseCreationEntry(String caseRef) {
        log.info("Creating {} for case {}", getClass().getSimpleName(), caseRef);
    }

    private CcdAuthenticator authenticateJurisdiction(String jurisdiction) {
        return authenticatorFactory.createForJurisdiction(jurisdiction);
    }

    String getCaseRef(Envelope envelope) {
        return envelope.caseRef;
    }

    private StartEventResponse startEvent(
        CcdAuthenticator authenticator,
        Envelope envelope,
        String caseTypeId
    ) {
        String caseRef = getCaseRef(envelope);
        String jurisdiction = envelope.jurisdiction;

        if (caseRef == null) {
            return startEventForNonExistingCase(authenticator, envelope, caseTypeId, jurisdiction);
        } else {
            return startEventForExistingCase(authenticator, envelope, caseTypeId, caseRef, jurisdiction);
        }
    }

    private StartEventResponse startEventForExistingCase(
        CcdAuthenticator authenticator,
        Envelope envelope,
        String caseTypeId,
        String caseRef,
        String jurisdiction
    ) {
        StartEventResponse response = ccdApi.startEventForCaseWorker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            caseRef,
            getEventTypeId()
        );

        log.info(
            "Started CCD event of type {} for envelope with ID {}, file name {}, case ref {}, case type {}",
            getEventTypeId(),
            envelope.id,
            envelope.zipFileName,
            envelope.caseRef,
            caseTypeId
        );

        return response;
    }

    private StartEventResponse startEventForNonExistingCase(
        CcdAuthenticator authenticator,
        Envelope envelope,
        String caseTypeId,
        String jurisdiction
    ) {
        StartEventResponse response = ccdApi.startForCaseworker(
            authenticator.getUserToken(),
            authenticator.getServiceToken(),
            authenticator.getUserDetails().getId(),
            jurisdiction,
            caseTypeId,
            getEventTypeId()
        );

        log.info(
            "Started CCD event of type {} (no case) for envelope with ID {}, file name {}, case type {}",
            getEventTypeId(),
            envelope.id,
            envelope.zipFileName,
            caseTypeId
        );

        return response;
    }

    abstract CaseData mapEnvelopeToCaseDataObject(Envelope envelope);

    CaseDataContent buildCaseDataContent(
        StartEventResponse eventResponse,
        Envelope envelope
    ) {
        CaseData caseDataObject = mapEnvelopeToCaseDataObject(envelope);
        return CaseDataContent.builder()
            .eventToken(eventResponse.getToken())
            .event(Event.builder()
                .id(getEventTypeId())
                .summary(getEventSummary())
                .build())
            .data(caseDataObject)
            .build();
    }

    private void submitEvent(
        CcdAuthenticator authenticator,
        Envelope envelope,
        CaseDataContent caseDataContent,
        String caseTypeId
    ) {
        String caseRef = getCaseRef(envelope);
        String jurisdiction = envelope.jurisdiction;

        if (caseRef == null) {
            ccdApi.submitForCaseworker(
                authenticator.getUserToken(),
                authenticator.getServiceToken(),
                authenticator.getUserDetails().getId(),
                jurisdiction,
                caseTypeId,
                true,
                caseDataContent
            );
        } else {
            ccdApi.submitEventForCaseWorker(
                authenticator.getUserToken(),
                authenticator.getServiceToken(),
                authenticator.getUserDetails().getId(),
                jurisdiction,
                caseTypeId,
                caseRef,
                true,
                caseDataContent
            );
        }

        log.info(
            "Submitted CCD event for envelope. Envelope ID: {}, file name: {}",
            envelope.id,
            envelope.zipFileName
        );
    }

    // end region - execution steps

    /**
     * EventPublisher is coupled with event type id. Used in building case data.
     * @return Event type ID
     */
    abstract String getEventTypeId();

    /**
     * Short sentence representing event type ID. Used in building case data.
     * @return Event summary
     */
    abstract String getEventSummary();
}
