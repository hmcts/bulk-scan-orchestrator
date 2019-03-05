package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd.CaseData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdApi;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
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
 * class PublisherName extends AbstractEventPublisher&lt;Envelope&gt; {
 *
 *     PublisherName() {
 *         // any extra autowiring needed
 *     }
 *
 *     @Override
 *     String getCaseReference(Envelope eventSource) {
 *         return "";
 *     }
 *
 *     @Override
 *     CaseData buildCaseData(StartEventResponse eventResponse, Envelope eventSource) {
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
abstract class AbstractEventPublisher<T> {

    @Autowired
    private CcdApi ccdApi;

    AbstractEventPublisher() {
    }

    protected void publish(T eventSource, String caseTypeId, String eventTypeId, String eventSummary) {
        LoggableEvent event = LoggableEvent.getInstance(eventSource);
        String caseRef = getCaseReference(eventSource);
        String jurisdiction = event.getJurisdiction();

        CcdAuthenticator authenticator = ccdApi.authenticateJurisdiction(jurisdiction);
        StartEventResponse startEventResponse = ccdApi.startEvent(
            authenticator,
            jurisdiction,
            caseTypeId,
            caseRef,
            eventTypeId
        );
        event.logEventStart(eventTypeId, caseRef == null ? "NO_CASE" : caseRef, caseTypeId);
        CaseDataContent caseDataContent = buildCaseDataContent(
            startEventResponse,
            eventSource,
            eventTypeId,
            eventSummary
        );
        CaseDetails submitEventResponse = ccdApi.submitEvent(
            authenticator,
            jurisdiction,
            caseTypeId,
            caseRef,
            caseDataContent
        );
        event.logSubmitEvent(caseTypeId, eventTypeId, submitEventResponse.getId());
    }

    private CaseDataContent buildCaseDataContent(
        StartEventResponse eventResponse,
        T eventSource,
        String eventTypeId,
        String eventSummary
    ) {
        CaseData caseDataObject = buildCaseData(eventResponse, eventSource);

        return CaseDataContent.builder()
            .eventToken(eventResponse.getToken())
            .event(Event.builder()
                .id(eventTypeId)
                .summary(eventSummary)
                .build())
            .data(caseDataObject)
            .build();
    }

    abstract String getCaseReference(T eventSource);

    /**
     * Build case data with help of event source and event start response.
     * @param eventResponse Response from event start
     * @param eventSource Event source
     * @return Case data
     */
    abstract CaseData buildCaseData(StartEventResponse eventResponse, T eventSource);
}
