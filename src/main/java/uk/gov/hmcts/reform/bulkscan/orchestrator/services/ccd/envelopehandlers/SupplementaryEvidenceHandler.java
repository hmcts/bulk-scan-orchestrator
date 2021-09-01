package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.AUTO_ATTACHED_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@Service
public class SupplementaryEvidenceHandler {

    private static final Logger log = LoggerFactory.getLogger(SupplementaryEvidenceHandler.class);

    private final CaseFinder caseFinder;
    private final AttachDocsToSupplementaryEvidence evidenceAttacher;
    private final PaymentsProcessor paymentsProcessor;
    private final CreateExceptionRecord exceptionRecordCreator;

    public SupplementaryEvidenceHandler(
        CaseFinder caseFinder,
        AttachDocsToSupplementaryEvidence evidenceAttacher,
        PaymentsProcessor paymentsProcessor,
        CreateExceptionRecord exceptionRecordCreator
    ) {
        this.caseFinder = caseFinder;
        this.evidenceAttacher = evidenceAttacher;
        this.paymentsProcessor = paymentsProcessor;
        this.exceptionRecordCreator = exceptionRecordCreator;
    }

    public EnvelopeProcessingResult handle(Envelope envelope) {
        checkArgument(
            envelope.classification == Classification.SUPPLEMENTARY_EVIDENCE,
            "Envelope classification has to be " + Classification.SUPPLEMENTARY_EVIDENCE
        );

        Optional<CaseDetails> caseDetailsFound = caseFinder.findCase(envelope);

        if (caseDetailsFound.isPresent()) {
            CaseDetails existingCase = caseDetailsFound.get();
            boolean docsAttached = evidenceAttacher.attach(envelope, existingCase);
            if (docsAttached) {
                paymentsProcessor.createPayments(envelope, existingCase.getId(), false);
                return new EnvelopeProcessingResult(existingCase.getId(), AUTO_ATTACHED_TO_CASE);
            } else {
                log.info(
                    "Creating exception record because attaching supplementary evidence to a case failed. "
                        + "envelope: {}, case: {}",
                    envelope.id,
                    existingCase.getId()
                );
                Long erId = exceptionRecordCreator.tryCreateFrom(envelope);
                paymentsProcessor.createPayments(envelope, erId, true);
                return new EnvelopeProcessingResult(erId, EXCEPTION_RECORD);
            }
        } else {
            log.info(
                    "Case not found, caseRef {}, legacyCaseRef {}. Creating exception record instead",
                    envelope.caseRef,
                    envelope.legacyCaseRef
            );
            Long erId = exceptionRecordCreator.tryCreateFrom(envelope);
            paymentsProcessor.createPayments(envelope, erId, true);
            return new EnvelopeProcessingResult(erId, EXCEPTION_RECORD);
        }
    }
}
