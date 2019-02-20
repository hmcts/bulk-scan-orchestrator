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
@SuppressWarnings("squid:S1612")
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

                return caseDetails == null
                    ? envelope -> exceptionRecordCreator.publish(envelope)
                    : envelope -> attachDocsPublisher.publish(envelope, caseDetails.getCaseTypeId());
            case EXCEPTION:
            case NEW_APPLICATION:
                return envelope -> exceptionRecordCreator.publish(envelope);
            default:
                throw new PublisherResolutionException(
                    "Cannot resolve publisher - unrecognised envelope classification: " + envelopeClassification
                );
        }
    }
}
