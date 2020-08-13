package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.AutoCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseCreationException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.AUTO_ATTACHED_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.CASE_CREATED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@Service
public class EnvelopeHandler {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeHandler.class);
    private static final int MAX_RETRIES_FOR_POTENTIALLY_RECOVERABLE_FAILURES = 2;

    private final AttachDocsToSupplementaryEvidence evidenceAttacher;
    private final CreateExceptionRecord exceptionRecordCreator;
    private final CaseFinder caseFinder;
    private final PaymentsProcessor paymentsProcessor;
    private final AutoCaseCreator caseCreator;

    public EnvelopeHandler(
        AttachDocsToSupplementaryEvidence evidenceAttacher,
        CreateExceptionRecord exceptionRecordCreator,
        CaseFinder caseFinder,
        PaymentsProcessor paymentsProcessor,
        AutoCaseCreator caseCreator
    ) {
        this.evidenceAttacher = evidenceAttacher;
        this.exceptionRecordCreator = exceptionRecordCreator;
        this.caseFinder = caseFinder;
        this.paymentsProcessor = paymentsProcessor;
        this.caseCreator = caseCreator;
    }

    public EnvelopeProcessingResult handleEnvelope(Envelope envelope, long deliveryCount) {
        switch (envelope.classification) {
            case SUPPLEMENTARY_EVIDENCE:
                Optional<CaseDetails> caseDetailsFound = caseFinder.findCase(envelope);

                if (caseDetailsFound.isPresent()) {
                    CaseDetails existingCase = caseDetailsFound.get();
                    boolean docsAttached = evidenceAttacher.attach(envelope, existingCase);
                    if (docsAttached) {
                        paymentsProcessor.createPayments(envelope, existingCase.getId(), false);
                        return new EnvelopeProcessingResult(existingCase.getId(), AUTO_ATTACHED_TO_CASE);
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
                return new EnvelopeProcessingResult(createExceptionRecord(envelope), EXCEPTION_RECORD);
            case NEW_APPLICATION:
                return processNewApplication(envelope, deliveryCount);
            default:
                throw new UnknownClassificationException(
                    "Cannot determine CCD action for envelope - unknown classification: " + envelope.classification
                );
        }
    }

    private EnvelopeProcessingResult processNewApplication(Envelope envelope, long deliveryCount) {
        var caseCreationResult = caseCreator.createCase(envelope);

        switch (caseCreationResult.resultType) {
            case CASE_CREATED:
            case CASE_ALREADY_EXISTS:
                paymentsProcessor.createPayments(envelope, caseCreationResult.caseCcdId, false);
                return new EnvelopeProcessingResult(caseCreationResult.caseCcdId, CASE_CREATED);
            case ABORTED_WITHOUT_FAILURE:
            case UNRECOVERABLE_FAILURE:
                return new EnvelopeProcessingResult(createExceptionRecord(envelope), EXCEPTION_RECORD);
            case POTENTIALLY_RECOVERABLE_FAILURE:
                if (deliveryCount < MAX_RETRIES_FOR_POTENTIALLY_RECOVERABLE_FAILURES) {
                    // let the application retry later
                    throw new CaseCreationException("Case creation failed due to a potentially recoverable error");
                } else {
                    // too many attempts - fall back to exception record
                    return new EnvelopeProcessingResult(createExceptionRecord(envelope), EXCEPTION_RECORD);
                }
            default:
                throw new CaseCreationException(
                    format(
                        "Failed to process case creation result - unknown result type: '%s'",
                        caseCreationResult.resultType
                    )
                );
        }
    }

    private Long createExceptionRecord(Envelope envelope) {
        Long ccdId = exceptionRecordCreator.tryCreateFrom(envelope);

        paymentsProcessor.createPayments(envelope, ccdId, true);
        return ccdId;
    }
}
