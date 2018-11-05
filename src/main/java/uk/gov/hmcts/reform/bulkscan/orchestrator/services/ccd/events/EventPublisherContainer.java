package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import com.google.common.base.Strings;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import javax.validation.constraints.NotNull;

/**
 * Container class to hold availableStrategies strategies enabled by this project.
 * In order to enable one must do:
 * <ul>
 *     <li>implement {@link AbstractEventPublisher}</li>
 *     <li>include {@code private EventPublisher somePublisher;}</li>
 *     <li>use resource in {@link this#getPublisher(Envelope)}</li>
 * </ul>
 */
@Component
public class EventPublisherContainer {

    private final AttachDocsToSupplementaryEvidence attachDocsPublisher;
    private final CreateExceptionRecord exceptionRecordCreator;

    private final CaseRetriever caseRetriever;

    EventPublisherContainer(
        AttachDocsToSupplementaryEvidence attachDocsPublisher,
        CreateExceptionRecord exceptionRecordCreator,
        CaseRetriever caseRetriever
    ) {
        this.attachDocsPublisher = attachDocsPublisher;
        this.exceptionRecordCreator = exceptionRecordCreator;
        this.caseRetriever = caseRetriever;
    }

    @NotNull
    public EventPublisher getPublisher(Envelope envelope) {
        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                CaseDetails caseDetails = Strings.isNullOrEmpty(envelope.caseRef)
                    ? null
                    : caseRetriever.retrieve(envelope.jurisdiction, envelope.caseRef);

                return new DelegatePublisher(
                    caseDetails == null ? exceptionRecordCreator : attachDocsPublisher,
                    caseDetails
                );
            case EXCEPTION:
                return new DelegatePublisher(exceptionRecordCreator, null);
            case NEW_APPLICATION:
            default:
                return new DelegatePublisher(null, null);
        }
    }
}
