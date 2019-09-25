package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

@Service
public class EnvelopeHandler {

    private final AttachDocsToSupplementaryEvidence evidenceAttacher;
    private final CreateExceptionRecord exceptionRecordCreator;
    private final CaseFinder caseFinder;
    private final PaymentsPublisher paymentsPublisher;

    public EnvelopeHandler(
        AttachDocsToSupplementaryEvidence evidenceAttacher,
        CreateExceptionRecord exceptionRecordCreator,
        CaseFinder caseFinder,
        PaymentsPublisher paymentsPublisher) {
        this.evidenceAttacher = evidenceAttacher;
        this.exceptionRecordCreator = exceptionRecordCreator;
        this.caseFinder = caseFinder;
        this.paymentsPublisher = paymentsPublisher;
    }

    public void handleEnvelope(Envelope envelope) {
        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                Optional<CaseDetails> caseDetailsOpt = caseFinder.findCase(envelope);

                if (caseDetailsOpt.isPresent()) {
                    evidenceAttacher.attach(envelope, caseDetailsOpt.get());
                } else {
                    exceptionRecordCreator.createFrom(envelope);
                }

                break;
            case EXCEPTION:
                exceptionRecordCreator.createFrom(envelope);
                break;
            case NEW_APPLICATION:
                CaseDetails caseDetails = exceptionRecordCreator.createFrom(envelope);
                paymentsPublisher.publishPayments(envelope, caseDetails, true);
                break;
            default:
                throw new UnknownClassificationException(
                    "Cannot determine CCD action for envelope - unknown classification: " + envelope.classification
                );
        }
    }
}
