package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

/**
 * Container class to hold availableStrategies strategies enabled by this project.
 * In order to enable one must do:
 * <ul>
 *     <li>implement {@link AbstractEventPublisher}</li>
 *     <li>include {@code private EventPublisher somePublisher;}</li>
 *     <li>use resource in {@link this#getPublisher(Envelope, CaseDetails)}</li>
 * </ul>
 */
@Component
public class EventPublisherContainer {

    private final EventPublisher attachDocsPublisher;

    EventPublisherContainer(
        AttachDocsToSupplementaryEvidence attachDocsPublisher
    ) {
        this.attachDocsPublisher = attachDocsPublisher;
    }

    public EventPublisher getPublisher(Envelope envelope, CaseDetails caseDetails) {
        EventPublisher eventPublisher = null;

        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                eventPublisher = attachDocsPublisher;

                break;
            case EXCEPTION:
            case NEW_APPLICATION:
            default:
                break;
        }

        return eventPublisher;
    }
}
