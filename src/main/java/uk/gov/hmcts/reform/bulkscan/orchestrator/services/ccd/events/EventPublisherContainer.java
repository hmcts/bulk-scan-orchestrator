package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import com.google.common.base.Strings;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseTypeId;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

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

    private final CaseRetriever caseRetriever;

    private final EventPublisher attachDocsPublisher;

    private final EventPublisher exceptionRecordCreator;

    EventPublisherContainer(
        CaseRetriever caseRetriever,
        AttachDocsToSupplementaryEvidence attachDocsPublisher,
        CreateExceptionRecord exceptionRecordCreator
    ) {
        this.caseRetriever = caseRetriever;
        this.attachDocsPublisher = attachDocsPublisher;
        this.exceptionRecordCreator = exceptionRecordCreator;
    }

    public EventPublisher getPublisher(Envelope envelope) {
        EventPublisher eventPublisher = null;

        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                boolean caseExists = doesCaseExist(
                    envelope.jurisdiction,
                    attachDocsPublisher.getCaseTypeIdForEvent(),
                    envelope.caseRef
                );
                eventPublisher = caseExists ? attachDocsPublisher : exceptionRecordCreator;

                break;
            case EXCEPTION:
                eventPublisher = exceptionRecordCreator;

                break;
            case NEW_APPLICATION:
            default:
                break;
        }

        return eventPublisher;
    }

    private boolean doesCaseExist(String jurisdiction, CaseTypeId caseTypeId, String caseRef) {
        CaseDetails caseDetails = Strings.isNullOrEmpty(caseRef)
            ? null
            : caseRetriever.retrieve(jurisdiction, caseTypeId, caseRef);

        return caseDetails != null;
    }
}
