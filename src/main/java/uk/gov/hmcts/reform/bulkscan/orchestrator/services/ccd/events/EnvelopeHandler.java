package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

@Service
public class EnvelopeHandler {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeHandler.class);

    private final AttachDocsToSupplementaryEvidence evidenceAttacher;
    private final CreateExceptionRecord exceptionRecordCreator;
    private final CaseFinder caseFinder;
    private final PaymentsProcessor paymentsProcessor;

    public EnvelopeHandler(
        AttachDocsToSupplementaryEvidence evidenceAttacher,
        CreateExceptionRecord exceptionRecordCreator,
        CaseFinder caseFinder,
        PaymentsProcessor paymentsProcessor
    ) {
        this.evidenceAttacher = evidenceAttacher;
        this.exceptionRecordCreator = exceptionRecordCreator;
        this.caseFinder = caseFinder;
        this.paymentsProcessor = paymentsProcessor;
    }

    public void handleEnvelope(Envelope envelope) {
        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                Optional<CaseDetails> caseDetailsFound = caseFinder.findCase(envelope);

                if (caseDetailsFound.isPresent()) {
                    CaseDetails existingCase = caseDetailsFound.get();
                    boolean docsAttached = evidenceAttacher.attach(envelope, existingCase);
                    if (docsAttached) {
                        paymentsProcessor.createPayments(envelope, existingCase.getId(), false);
                    } else {
                        log.info(
                            "Adding supplementary evidence from envelope {} to case {} failed. "
                                + "Creating exception record instead.",
                            envelope.id,
                            existingCase.getId()
                        );
                        createExceptionRecord(envelope);
                    }
                } else {
                    log.info("Case for envelope {} not found. Creating exception record instead.", envelope.id);
                    createExceptionRecord(envelope);
                }
                break;
            case SUPPLEMENTARY_EVIDENCE_WITH_OCR:
            case EXCEPTION:
            case NEW_APPLICATION:
                createExceptionRecord(envelope);
                break;
            default:
                throw new UnknownClassificationException(
                    "Cannot determine CCD action for envelope - unknown classification: " + envelope.classification
                );
        }
    }

    private void createExceptionRecord(Envelope envelope) {
        Long ccdId = exceptionRecordCreator.tryCreateFrom(envelope);

        paymentsProcessor.createPayments(envelope, ccdId, true);
    }
}
