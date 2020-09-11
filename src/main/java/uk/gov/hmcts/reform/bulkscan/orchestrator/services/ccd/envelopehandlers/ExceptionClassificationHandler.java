package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

import static com.google.common.base.Preconditions.checkArgument;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@Service
public class ExceptionClassificationHandler {

    private final CreateExceptionRecord exceptionRecordCreator;
    private final PaymentsProcessor paymentsProcessor;

    public ExceptionClassificationHandler(
        CreateExceptionRecord exceptionRecordCreator,
        PaymentsProcessor paymentsProcessor
    ) {
        this.exceptionRecordCreator = exceptionRecordCreator;
        this.paymentsProcessor = paymentsProcessor;
    }

    public EnvelopeProcessingResult handle(Envelope envelope) {
        checkArgument(
            envelope.classification == Classification.EXCEPTION,
            "Envelope classification has to be " + Classification.EXCEPTION
        );

        Long ccdId = exceptionRecordCreator.tryCreateFrom(envelope);
        paymentsProcessor.createPayments(envelope, ccdId, true);

        return new EnvelopeProcessingResult(ccdId, EXCEPTION_RECORD);
    }
}
