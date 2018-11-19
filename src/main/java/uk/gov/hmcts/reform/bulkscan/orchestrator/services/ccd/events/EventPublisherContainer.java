package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.function.Supplier;
import javax.validation.constraints.NotNull;

/**
 * Container class to hold availableStrategies strategies enabled by this project.
 * In order to enable one must do:
 * <ul>
 *     <li>implement {@link AbstractEventPublisher}</li>
 *     <li>include {@code private EventPublisher somePublisher;}</li>
 *     <li>use resource in {@link this#getPublisher(Classification, Supplier)})}</li>
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

    @NotNull
    public EventPublisher getPublisher(
        Classification envelopeClassification,
        Supplier<CaseDetails> caseRetrieval
    ) {
        switch (envelopeClassification) {
            case SUPPLEMENTARY_EVIDENCE:
                CaseDetails caseDetails = caseRetrieval.get();

                return caseDetails == null ? exceptionRecordCreator : new DelegatePublisher(
                    attachDocsPublisher,
                    caseDetails
                );
            case EXCEPTION:
                return exceptionRecordCreator;
            case NEW_APPLICATION:
            default:
                return new DelegatePublisher(null, null);
        }
    }
}
