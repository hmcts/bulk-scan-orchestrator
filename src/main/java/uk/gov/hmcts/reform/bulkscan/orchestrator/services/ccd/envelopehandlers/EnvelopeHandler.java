package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

@Service
public class EnvelopeHandler {

    private final ExceptionClassificationHandler exceptionClassificationHandler;
    private final SupplementaryEvidenceWithOcrHandler supplementaryEvidenceWithOcrHandler;
    private final NewApplicationHandler newApplicationHandler;
    private final SupplementaryEvidenceHandler supplementaryEvidenceHandler;

    public EnvelopeHandler(
        ExceptionClassificationHandler exceptionClassificationHandler,
        SupplementaryEvidenceWithOcrHandler supplementaryEvidenceWithOcrHandler,
        NewApplicationHandler newApplicationHandler,
        SupplementaryEvidenceHandler supplementaryEvidenceHandler
    ) {
        this.exceptionClassificationHandler = exceptionClassificationHandler;
        this.supplementaryEvidenceWithOcrHandler = supplementaryEvidenceWithOcrHandler;
        this.newApplicationHandler = newApplicationHandler;
        this.supplementaryEvidenceHandler = supplementaryEvidenceHandler;
    }

    public EnvelopeProcessingResult handleEnvelope(Envelope envelope, long deliveryCount) {
        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                return supplementaryEvidenceHandler.handle(envelope);
            case SUPPLEMENTARY_EVIDENCE_WITH_OCR:
                return supplementaryEvidenceWithOcrHandler.handle(envelope, deliveryCount);
            case EXCEPTION:
                return exceptionClassificationHandler.handle(envelope);
            case NEW_APPLICATION:
                return newApplicationHandler.handle(envelope, deliveryCount);
            default:
                throw new UnknownClassificationException(
                    "Cannot determine CCD action for envelope - unknown classification: " + envelope.classification
                );
        }
    }
}
