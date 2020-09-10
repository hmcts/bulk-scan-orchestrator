package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@Service
public class EnvelopeHandler {

    private final CreateExceptionRecord exceptionRecordCreator;
    private final PaymentsProcessor paymentsProcessor;
    private final NewApplicationHandler newApplicationHandler;
    private final SupplementaryEvidenceHandler supplementaryEvidenceHandler;

    public EnvelopeHandler(
        CreateExceptionRecord exceptionRecordCreator,
        PaymentsProcessor paymentsProcessor,
        SupplementaryEvidenceHandler supplementaryEvidenceHandler,
        NewApplicationHandler newApplicationHandler
    ) {
        this.exceptionRecordCreator = exceptionRecordCreator;
        this.paymentsProcessor = paymentsProcessor;
        this.supplementaryEvidenceHandler = supplementaryEvidenceHandler;
        this.newApplicationHandler = newApplicationHandler;
    }

    public EnvelopeProcessingResult handleEnvelope(Envelope envelope, long deliveryCount) {
        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                return supplementaryEvidenceHandler.handle(envelope);
            case SUPPLEMENTARY_EVIDENCE_WITH_OCR:
            case EXCEPTION:
                return new EnvelopeProcessingResult(createExceptionRecord(envelope), EXCEPTION_RECORD);
            case NEW_APPLICATION:
                return newApplicationHandler.handle(envelope, deliveryCount);
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
