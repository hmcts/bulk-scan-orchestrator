package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseFinder;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.PaymentsProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Optional;

@Service
public class EnvelopeHandler {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeHandler.class);

    private final AttachDocsToSupplementaryEvidence evidenceAttacher;
    private final CreateExceptionRecord exceptionRecordCreator;
    private final CaseFinder caseFinder;
    private final PaymentsProcessor paymentsProcessor;
    private final List<String> supportedJurisdictions;

    public EnvelopeHandler(
        AttachDocsToSupplementaryEvidence evidenceAttacher,
        CreateExceptionRecord exceptionRecordCreator,
        CaseFinder caseFinder,
        PaymentsProcessor paymentsProcessor,
        @Value("${supported-jurisdictions}") List<String> supportedJurisdictions
    ) {
        this.evidenceAttacher = evidenceAttacher;
        this.exceptionRecordCreator = exceptionRecordCreator;
        this.caseFinder = caseFinder;
        this.paymentsProcessor = paymentsProcessor;
        this.supportedJurisdictions = supportedJurisdictions;
    }

    public void handleEnvelope(Envelope envelope) {
        // check if envelope jurisdiction is configured
        checkJurisdictionConfigured(envelope);

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
                            "Creating exception record as supplementary evidence failed for envelope {} case {}",
                            envelope.id,
                            existingCase.getId()
                        );
                        createExceptionRecord(envelope);
                    }
                } else {
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

    private void checkJurisdictionConfigured(Envelope envelope) {
        if (!supportedJurisdictions.contains(envelope.jurisdiction)) {
            log.info(
                "Jurisdiction is not supported for envelope {} file {} Jurisdiction {}",
                envelope.id,
                envelope.zipFileName,
                envelope.jurisdiction
            );
            throw new UnsupportedJurisdictionException(
                "Envelope jurisdiction is not supported: " + envelope.jurisdiction
            );
        }
    }

    private void createExceptionRecord(Envelope envelope) {
        Long ccdId = exceptionRecordCreator.tryCreateFrom(envelope);

        paymentsProcessor.createPayments(envelope, ccdId, true);
    }
}
