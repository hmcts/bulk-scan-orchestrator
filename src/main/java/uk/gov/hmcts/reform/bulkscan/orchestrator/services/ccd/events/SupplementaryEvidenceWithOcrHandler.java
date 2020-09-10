package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@Service
public class SupplementaryEvidenceWithOcrHandler {

    private final CreateExceptionRecord exceptionRecordCreator;
    private final PaymentsProcessor paymentsProcessor;

    public SupplementaryEvidenceWithOcrHandler(
        CreateExceptionRecord exceptionRecordCreator,
        PaymentsProcessor paymentsProcessor
    ) {
        this.exceptionRecordCreator = exceptionRecordCreator;
        this.paymentsProcessor = paymentsProcessor;
    }

    public EnvelopeProcessingResult handle(Envelope envelope) {
        Long ccdId = exceptionRecordCreator.tryCreateFrom(envelope);
        paymentsProcessor.createPayments(envelope, ccdId, true);

        return new EnvelopeProcessingResult(ccdId, EXCEPTION_RECORD);
    }
}
