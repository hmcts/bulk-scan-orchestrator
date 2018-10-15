package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import javax.annotation.Resource;

/**
 * Container class to hold availableStrategies strategies enabled by this project.
 * In order to enable one must do:
 * <ul>
 *     <li>implement {@link AbstractStrategy}</li>
 *     <li>include {@code @Resource(name = "publisher-name") private EventPublisher somePublisher;}</li>
 *     <li>use resource in {@link this#getPublisher(Envelope, CaseDetails)}</li>
 * </ul>
 */
@Component
public class EventPublisherContainer {

    @Resource(name = "attach-docs-to-supplementary-evidence")
    private EventPublisher attachDocsPublisher;

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

    EventPublisherContainer() {
        // utility class constructor
    }
}
