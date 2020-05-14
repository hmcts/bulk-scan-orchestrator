package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.AUTO_ATTACHED_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

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

    public EnvelopeProcessingResult handleEnvelope(Envelope envelope) {
        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                Optional<CaseDetails> caseDetailsFound = caseFinder.findCase(envelope);

                if (caseDetailsFound.isPresent()) {
                    CaseDetails existingCase = caseDetailsFound.get();
                    boolean docsAttached = evidenceAttacher.attach(envelope, existingCase);
                    if (docsAttached) {
                        paymentsProcessor.createPayments(envelope, existingCase.getId(), false);
                        return new EnvelopeProcessingResult(existingCase.getId(),  AUTO_ATTACHED_TO_CASE);
                    } else {
                        log.info(
                            "Creating exception record as supplementary evidence failed for envelope {} case {}",
                            envelope.id,
                            existingCase.getId()
                        );
                        return new EnvelopeProcessingResult(createExceptionRecord(envelope), EXCEPTION_RECORD);
                    }
                } else {
                    return new EnvelopeProcessingResult(createExceptionRecord(envelope), EXCEPTION_RECORD);
                }
            case SUPPLEMENTARY_EVIDENCE_WITH_OCR:
            case EXCEPTION:
            case NEW_APPLICATION:
                return new EnvelopeProcessingResult(createExceptionRecord(envelope), EXCEPTION_RECORD);
            default:
                throw new UnknownClassificationException(
                    "Cannot determine CCD action for envelope - unknown classification: " + envelope.classification
                );
        }
    }

    private Long createExceptionRecord(Envelope envelope) {
        Long ccdId = exceptionRecordCreator.tryCreateFrom(envelope);

        paymentsProcessor.createPayments(envelope, ccdId, true);
        return ccdId;
    }
}
