package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.autocaseupdate.AutoCaseUpdater;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

import static com.google.common.base.Preconditions.checkArgument;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.AUTO_UPDATED_CASE;
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
            var updateResult = autoCaseUpdater.updateCase(envelope);

            switch (updateResult.type) {
                case OK:
                    paymentsProcessor.createPayments(envelope, updateResult.caseId, false);
                    return new EnvelopeProcessingResult(updateResult.caseId, AUTO_UPDATED_CASE);
                case ERROR:
                    // failed, let's try again later...
                    // TODO: limit number of attempts and create exception record
                    throw new CaseUpdateException("Updating case failed due to a potentially recoverable error");
                case ABANDONED:
                    // it's not possible to update a case...
                    return createExceptionRecord(envelope);
                default:
                    throw new CaseUpdateException("Unsupported result type: " + updateResult.type);
            }
        } else {
           return createExceptionRecord(envelope);
        }
    }

    private EnvelopeProcessingResult createExceptionRecord(Envelope envelope) {
        Long ccdId = exceptionRecordCreator.tryCreateFrom(envelope);
        paymentsProcessor.createPayments(envelope, ccdId, true);
        return new EnvelopeProcessingResult(ccdId, EXCEPTION_RECORD);
    }
}
