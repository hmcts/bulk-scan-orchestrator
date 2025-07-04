package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.PaymentsService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.AutoCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.casecreation.CaseCreationException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.AUTO_CREATED_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

/**
 * Handles envelopes of classification NEW_APPLICATION.
 */
@Service
public class NewApplicationHandler {

    public static final int MAX_RETRIES_FOR_POTENTIALLY_RECOVERABLE_FAILURES = 2;

    private final AutoCaseCreator caseCreator;
    private final PaymentsService paymentsService;
    private final CreateExceptionRecord exceptionRecordCreator;

    public NewApplicationHandler(
        AutoCaseCreator caseCreator,
        PaymentsService paymentsService,
        CreateExceptionRecord exceptionRecordCreator
    ) {
        this.caseCreator = caseCreator;
        this.paymentsService = paymentsService;
        this.exceptionRecordCreator = exceptionRecordCreator;
    }

    public EnvelopeProcessingResult handle(Envelope envelope, long deliveryCount) {
        checkArgument(
            envelope.classification == Classification.NEW_APPLICATION,
            "Envelope classification has to be " + Classification.NEW_APPLICATION
        );

        var caseCreationResult = caseCreator.createCase(envelope);

        switch (caseCreationResult.resultType) {
            case CASE_CREATED:
            case CASE_ALREADY_EXISTS:
                paymentsService.createNewPayment(envelope, caseCreationResult.caseCcdId, false);
                return new EnvelopeProcessingResult(caseCreationResult.caseCcdId, AUTO_CREATED_CASE);
            case ABORTED_WITHOUT_FAILURE:
            case UNRECOVERABLE_FAILURE:
                // we can't create a case - create exception record instead.
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

        paymentsService.createNewPayment(envelope, ccdId, true);
        return ccdId;
    }
}
