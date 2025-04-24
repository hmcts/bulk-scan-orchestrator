package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Either;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.NewCallbackResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.AlreadyAttachedToCaseException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.CaseNotFoundException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.DuplicateDocsException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.PaymentsPublishingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.data.callbackresult.NewCallbackResult.attachToCaseCaseRequest;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes.EXTERNAL_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ATTACH_TO_CASE_REFERENCE;

@Component
public class ExceptionRecordAttacher {

    private static final String PAYMENT_ERROR_MSG = "Payment references cannot be processed. Please try again later";

    private static final Logger log = LoggerFactory.getLogger(ExceptionRecordAttacher.class);

    private final SupplementaryEvidenceUpdater supplementaryEvidenceUpdater;
    private final SupplementaryEvidenceWithOcrUpdater supplementaryEvidenceWithOcrUpdater;
    private final PaymentsProcessor paymentsProcessor;
    private final CallbackResultRepositoryProxy callbackResultRepositoryProxy;
    private final CcdApi ccdApi;

    public ExceptionRecordAttacher(
        SupplementaryEvidenceUpdater supplementaryEvidenceUpdater,
        SupplementaryEvidenceWithOcrUpdater supplementaryEvidenceWithOcrUpdater,
        PaymentsProcessor paymentsProcessor,
        CallbackResultRepositoryProxy callbackResultRepositoryProxy,
        CcdApi ccdApi
    ) {
        this.supplementaryEvidenceUpdater = supplementaryEvidenceUpdater;
        this.supplementaryEvidenceWithOcrUpdater = supplementaryEvidenceWithOcrUpdater;
        this.paymentsProcessor = paymentsProcessor;
        this.callbackResultRepositoryProxy = callbackResultRepositoryProxy;
        this.ccdApi = ccdApi;
    }

    //The code below need to be rewritten to reuse the EventPublisher class

    /**
     * Attaches exception record to a case.
     *
     * @return Either an ID of case to which exception record was attached, when processing was successful,
     *         or the list of errors, in case of errors
     */
    public Either<ErrorsAndWarnings, String> tryAttachToCase(
        AttachToCaseEventData callBackEvent,
        CaseDetails exceptionRecordDetails,
        boolean ignoreWarnings
    ) {
        try {
            verifyExceptionRecordIsNotAttachedToCase(
                callBackEvent.exceptionRecordJurisdiction,
                callBackEvent.exceptionRecordId
            );

            log.info(
                "Attaching exception record '{}' to a case by reference type '{}' and reference '{}'",
                callBackEvent.exceptionRecordId,
                callBackEvent.targetCaseRefType,
                callBackEvent.targetCaseRef
            );

            return attachToCase(callBackEvent, ignoreWarnings)
                .peek(attachToCaseRef -> paymentsProcessor.updatePayments(
                    PaymentsHelper.create(exceptionRecordDetails),
                    Long.toString(callBackEvent.exceptionRecordId),
                    callBackEvent.exceptionRecordJurisdiction,
                    attachToCaseRef
                ));
        } catch (AlreadyAttachedToCaseException
                 | DuplicateDocsException
                 | CaseNotFoundException
                 | MultipleCasesFoundException
                 | InvalidCaseIdException exc
        ) {
            log.warn(
                "Validation error when attaching ER {} in {} to case {}",
                callBackEvent.exceptionRecordId,
                callBackEvent.exceptionRecordJurisdiction,
                callBackEvent.targetCaseRef,
                exc
            );
            return Either.left(ErrorsAndWarnings.withErrors(singletonList(exc.getMessage())));
        } catch (PaymentsPublishingException exception) {
            log.error(
                "Failed to send update to payment processor for {} exception record {}",
                callBackEvent.exceptionRecordJurisdiction,
                callBackEvent.exceptionRecordId,
                exception
            );
            return Either.left(ErrorsAndWarnings.withErrors(singletonList(PAYMENT_ERROR_MSG)));
        } catch (Exception exc) {
            log.error(
                "Error attaching ER {} in {} to case {}",
                callBackEvent.exceptionRecordId,
                callBackEvent.exceptionRecordJurisdiction,
                callBackEvent.targetCaseRef,
                exc
            );
            throw exc;
        }
    }

    private void verifyExceptionRecordIsNotAttachedToCase(
        String exceptionRecordJurisdiction,
        Long exceptionRecordReference
    ) {
        CaseDetails fetchedExceptionRecord = ccdApi.getCase(
            exceptionRecordReference.toString(),
            exceptionRecordJurisdiction
        );

        String attachToCaseRef = (String) fetchedExceptionRecord.getData().get(ATTACH_TO_CASE_REFERENCE);

        if (StringUtils.isNotEmpty(attachToCaseRef)) {
            throw new AlreadyAttachedToCaseException("Exception record is already attached to case " + attachToCaseRef);
        }
    }

    // target case ref on the right
    private Either<ErrorsAndWarnings, String> attachToCase(
        AttachToCaseEventData callBackEvent,
        boolean ignoreWarnings
    ) {
        String targetCaseRef = EXTERNAL_CASE_REFERENCE.equals(callBackEvent.targetCaseRefType)
            ? getTargetCaseRefFromLegacyId(
            callBackEvent.targetCaseRef,
            callBackEvent.service,
            callBackEvent.exceptionRecordId
        )
            : callBackEvent.targetCaseRef;

        return attachToCaseByCcdId(callBackEvent, targetCaseRef, ignoreWarnings)
            .map(Either::<ErrorsAndWarnings, String>left)
            .orElseGet(() -> {
                log.info(
                    "Completed the process of attaching exception record to a case. ER ID: {}. Case ID: {}",
                    callBackEvent.exceptionRecordId,
                    targetCaseRef
                );

                return Either.right(targetCaseRef);
            });
    }

    private String getTargetCaseRefFromLegacyId(String targetCaseRef, String service, long exceptionRecordId) {
        List<Long> targetCaseCcdIds = ccdApi.getCaseRefsByLegacyId(targetCaseRef, service);

        if (targetCaseCcdIds.size() == 1) {
            log.info(
                "Found case with CCD ID '{}' for legacy ID '{}' (attaching exception record '{}')",
                targetCaseCcdIds.get(0),
                targetCaseRef,
                exceptionRecordId
            );

            return Long.toString(targetCaseCcdIds.get(0));
        } else if (targetCaseCcdIds.isEmpty()) {
            throw new CaseNotFoundException(
                String.format("No case found for legacy case reference %s", targetCaseRef)
            );
        } else {
            throw new MultipleCasesFoundException(
                String.format(
                    "Multiple cases (%s) found for the given legacy case reference: %s",
                    targetCaseCcdIds.stream().map(String::valueOf).collect(joining(", ")),
                    targetCaseRef
                )
            );
        }
    }

    private Optional<ErrorsAndWarnings> attachToCaseByCcdId(
        AttachToCaseEventData callBackEvent,
        String targetCaseCcdRef,
        boolean ignoreWarnings
    ) {
        log.info(
            "Attaching exception record '{}' to a case by CCD ID '{}', exceptionRecordDocuments{} ExcepDocs {}",
            callBackEvent.exceptionRecordId,
            targetCaseCcdRef,
            callBackEvent.exceptionRecordDocuments,
            callBackEvent.exceptionRecord == null ? "null" : callBackEvent.exceptionRecord.scannedDocuments
        );

        CaseDetails targetCase = ccdApi.getCase(targetCaseCcdRef, callBackEvent.exceptionRecordJurisdiction);

        switch (callBackEvent.classification) {
            case EXCEPTION:
            case SUPPLEMENTARY_EVIDENCE:
                boolean attached = supplementaryEvidenceUpdater.updateSupplementaryEvidence(
                    callBackEvent,
                    targetCase,
                    targetCaseCcdRef
                );
                if (attached) {
                    storeCallbackResult(callBackEvent, targetCaseCcdRef);
                }
                return Optional.empty();

            case SUPPLEMENTARY_EVIDENCE_WITH_OCR:
                var errorsAndWarnings = supplementaryEvidenceWithOcrUpdater.updateSupplementaryEvidenceWithOcr(
                    callBackEvent,
                    targetCase,
                    targetCaseCcdRef,
                    ignoreWarnings
                );
                if (errorsAndWarnings.isEmpty()) {
                    storeCallbackResult(callBackEvent, targetCaseCcdRef);
                }
                return errorsAndWarnings;

            default:
                throw new CallbackException("Invalid Journey Classification: " + callBackEvent.classification);
        }
    }

    private void storeCallbackResult(AttachToCaseEventData callBackEvent, String targetCaseCcdRef) {
        NewCallbackResult callbackResult = attachToCaseCaseRequest(
            Long.toString(callBackEvent.exceptionRecordId),
            targetCaseCcdRef
        );
        callbackResultRepositoryProxy.storeCallbackResult(callbackResult);
    }
}
