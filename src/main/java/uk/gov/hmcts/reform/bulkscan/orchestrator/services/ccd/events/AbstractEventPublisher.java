package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
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
 * }}</pre>
 * <p/>
 * No need to make any public access as everything will be injected in {@link EventPublisherContainer}:
 * <pre>{@code
 * private final EventPublisher somePublisher;}</pre>
 * <p/>
 * Then include each publisher in {@link EventPublisherContainer}
 */
abstract class AbstractEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AbstractEventPublisher.class);

    @Autowired
    private CcdApi ccdApi;

    AbstractEventPublisher() {
    }

    protected void publish(Envelope envelope, String caseTypeId, String eventTypeId, String eventSummary) {
        CcdAuthenticator authenticator = ccdApi.authenticateJurisdiction(envelope.jurisdiction);
        StartEventResponse eventResponse = startEvent(authenticator, envelope, caseTypeId, eventTypeId);
        CaseDataContent caseDataContent = buildCaseDataContent(eventResponse, envelope, eventTypeId, eventSummary);
        submitEvent(authenticator, envelope, caseDataContent, caseTypeId, eventTypeId);
    }

    // region - execution steps

    String getCaseRef(Envelope envelope) {
        return envelope.caseRef;
    }

    private StartEventResponse startEvent(
        CcdAuthenticator authenticator,
        Envelope envelope,
        String caseTypeId,
        String eventTypeId
    ) {
        String caseRef = getCaseRef(envelope);

        StartEventResponse response = ccdApi.startEvent(
            authenticator,
            envelope.jurisdiction,
            caseTypeId,
            caseRef,
            eventTypeId
        );

        log.info(
            "Started CCD event of type {} for envelope with ID {}, file name {}, case ref {}, case type {}",
            eventTypeId,
            envelope.id,
            envelope.zipFileName,
            caseRef == null ? "NO_CASE" : caseRef,
            caseTypeId
        );

        return response;
    }

    abstract CaseData buildCaseData(StartEventResponse eventResponse, Envelope envelope);

    CaseDataContent buildCaseDataContent(
        StartEventResponse eventResponse,
        Envelope envelope,
        String eventTypeId,
        String eventSummary
    ) {
        CaseData caseDataObject = buildCaseData(eventResponse, envelope);

        return CaseDataContent.builder()
            .eventToken(eventResponse.getToken())
            .event(Event.builder()
                .id(eventTypeId)
                .summary(eventSummary)
                .build())
            .data(caseDataObject)
            .build();
    }

    private void submitEvent(
        CcdAuthenticator authenticator,
        Envelope envelope,
        CaseDataContent caseDataContent,
        String caseTypeId,
        String eventTypeId
    ) {
        CaseDetails response = ccdApi.submitEvent(
            authenticator,
            envelope.jurisdiction,
            caseTypeId,
            getCaseRef(envelope),
            caseDataContent
        );

        log.info(
            "Created CCD case of type {} and event of type {}. Envelope ID: {}, file name: {}, case ID: {}",
            caseTypeId,
            eventTypeId,
            envelope.id,
            envelope.zipFileName,
            response.getId()
        );
    }

    // end region - execution steps
}
