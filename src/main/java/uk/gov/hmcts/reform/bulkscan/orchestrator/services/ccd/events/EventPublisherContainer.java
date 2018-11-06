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

    private final AttachDocsToSupplementaryEvidence attachDocsPublisher;
    private final CreateExceptionRecord exceptionRecordCreator;

    EventPublisherContainer(
        AttachDocsToSupplementaryEvidence attachDocsPublisher,
        CreateExceptionRecord exceptionRecordCreator
    ) {
        this.attachDocsPublisher = attachDocsPublisher;
        this.exceptionRecordCreator = exceptionRecordCreator;
    }

    public EventPublisher getPublisher(Envelope envelope, CaseDetails caseDetails) {
        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                return caseDetails == null ? exceptionRecordCreator : attachDocsPublisher;
            case EXCEPTION:
                return exceptionRecordCreator;
            case NEW_APPLICATION:
            default:
                return null;
        }
    }
}
