package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import com.google.common.base.Strings;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

@Service
public class EnvelopeHandler {

    private final AttachDocsToSupplementaryEvidence attachDocsPublisher;
    private final CreateExceptionRecord exceptionRecordCreator;
    private final CaseRetriever caseRetriever;

    public EnvelopeHandler(
        AttachDocsToSupplementaryEvidence attachDocsPublisher,
        CreateExceptionRecord exceptionRecordCreator,
        CaseRetriever caseRetriever
    ) {
        this.attachDocsPublisher = attachDocsPublisher;
        this.exceptionRecordCreator = exceptionRecordCreator;
        this.caseRetriever = caseRetriever;
    }

    public void handleEnvelope(Envelope envelope) {
        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                CaseDetails caseDetails = Strings.isNullOrEmpty(envelope.caseRef)
                    ? null
                    : caseRetriever.retrieve(envelope.jurisdiction, envelope.caseRef);

                if (caseDetails == null) {
                    exceptionRecordCreator.publish(envelope);
                } else {
                    attachDocsPublisher.publish(envelope, caseDetails);
                }

                break;
            case EXCEPTION:
            case NEW_APPLICATION:
                exceptionRecordCreator.publish(envelope);
                break;
            default:
                throw new UnknownClassificationException(
                    "Cannot determine CCD action for envelope - unknown classification: " + envelope.classification
                );
        }
    }
}
