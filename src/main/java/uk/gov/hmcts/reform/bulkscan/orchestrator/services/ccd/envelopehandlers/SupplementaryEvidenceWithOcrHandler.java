package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

import static com.google.common.base.Preconditions.checkArgument;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@Service
public class SupplementaryEvidenceWithOcrHandler {

    private final CreateExceptionRecord exceptionRecordCreator;
    private final PaymentsProcessor paymentsProcessor;
    private final AutoCaseUpdater autoCaseUpdater;
    private final ServiceConfigProvider serviceConfigProvider;

    public SupplementaryEvidenceWithOcrHandler(
        CreateExceptionRecord exceptionRecordCreator,
        PaymentsProcessor paymentsProcessor,
        AutoCaseUpdater autoCaseUpdater,
        ServiceConfigProvider serviceConfigProvider
    ) {
        this.exceptionRecordCreator = exceptionRecordCreator;
        this.paymentsProcessor = paymentsProcessor;
        this.autoCaseUpdater = autoCaseUpdater;
        this.serviceConfigProvider = serviceConfigProvider;
    }

    public EnvelopeProcessingResult handle(Envelope envelope) {
        checkArgument(
            envelope.classification == Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR,
            "Envelope classification has to be " + Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR
        );

        if (serviceConfigProvider.getConfig(envelope.container).getAutoCaseUpdateEnabled()) {
            autoCaseUpdater.updateCase(envelope);
            paymentsProcessor.createPayments(envelope, ccdId, false);
            return new EnvelopeProcessingResult(ccdId, EXCEPTION_RECORD);
        } else {
            Long ccdId = exceptionRecordCreator.tryCreateFrom(envelope);
            paymentsProcessor.createPayments(envelope, ccdId, true);
            return new EnvelopeProcessingResult(ccdId, EXCEPTION_RECORD);
        }
    }
}
