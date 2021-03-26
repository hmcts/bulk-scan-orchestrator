package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.AttachScannedDocumentsValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.DuplicateDocsException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes.EXTERNAL_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ATTACH_TO_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.EVIDENCE_HANDLED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;

@Component
public class ExceptionRecordAttacher {

    private static final String PAYMENT_ERROR_MSG = "Payment references cannot be processed. Please try again later";

    private static final Logger log = LoggerFactory.getLogger(ExceptionRecordAttacher.class);

    private final ServiceConfigProvider serviceConfigProvider;
    private final PaymentsProcessor paymentsProcessor;
    private final CcdApi ccdApi;
    private final CcdCaseUpdater ccdCaseUpdater;
    private final AttachScannedDocumentsValidator scannedDocumentsValidator;

    public ExceptionRecordAttacher(ServiceConfigProvider serviceConfigProvider,
                                   PaymentsProcessor paymentsProcessor,
                                   CcdApi ccdApi,
                                   CcdCaseUpdater ccdCaseUpdater,
                                   AttachScannedDocumentsValidator scannedDocumentsValidator
    ) {
        this.serviceConfigProvider = serviceConfigProvider;
        this.paymentsProcessor = paymentsProcessor;
        this.ccdApi = ccdApi;
        this.ccdCaseUpdater = ccdCaseUpdater;
        this.scannedDocumentsValidator = scannedDocumentsValidator;
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
            "Attaching exception record '{}' to a case by CCD ID '{}'",
            callBackEvent.exceptionRecordId,
            targetCaseCcdRef
        );

        switch (callBackEvent.classification) {
            case EXCEPTION:
            case SUPPLEMENTARY_EVIDENCE:
                updateSupplementaryEvidence(
                    callBackEvent,
                    targetCaseCcdRef
                );
                return Optional.empty();

            case SUPPLEMENTARY_EVIDENCE_WITH_OCR:
                return updateSupplementaryEvidenceWithOcr(
                    callBackEvent,
                    targetCaseCcdRef,
                    ignoreWarnings
                );

            default:
                throw new CallbackException("Invalid Journey Classification: " + callBackEvent.classification);
        }
    }

    private void updateSupplementaryEvidence(
        AttachToCaseEventData callBackEvent,
        String targetCaseCcdRef
    ) {
        CaseDetails theCase = ccdApi.getCase(targetCaseCcdRef, callBackEvent.exceptionRecordJurisdiction);
        List<Map<String, Object>> targetCaseDocuments = Documents.getScannedDocuments(theCase);

        scannedDocumentsValidator.verifyExceptionRecordAddsNoDuplicates(
            targetCaseDocuments,
            callBackEvent.exceptionRecordDocuments,
            Long.toString(callBackEvent.exceptionRecordId),
            targetCaseCcdRef
        );

        List<Map<String, Object>> documentsToAttach = Documents.removeAlreadyAttachedDocuments(
            callBackEvent.exceptionRecordDocuments,
            targetCaseDocuments,
            Long.toString(callBackEvent.exceptionRecordId)
        );

        if (!documentsToAttach.isEmpty()) {
            List<Map<String, Object>> newCaseDocuments = attachExceptionRecordReference(
                documentsToAttach,
                callBackEvent.exceptionRecordId
            );

            StartEventResponse ccdStartEvent =
                ccdApi.startAttachScannedDocs(theCase, callBackEvent.idamToken, callBackEvent.userId);

            Map<String, Object> newCaseData = buildCaseData(newCaseDocuments, targetCaseDocuments);

            final String eventSummary = createEventSummary(theCase, callBackEvent.exceptionRecordId, newCaseDocuments);

            log.info(eventSummary);

            ccdApi.attachExceptionRecord(
                theCase,
                callBackEvent.idamToken,
                callBackEvent.userId,
                newCaseData,
                eventSummary,
                ccdStartEvent
            );

            log.info(
                "Attached Exception Record to a case in CCD. ER ID: {}. Case ID: {}",
                callBackEvent.exceptionRecordId,
                theCase.getId()
            );
        }
    }

    private Optional<ErrorsAndWarnings> updateSupplementaryEvidenceWithOcr(
        AttachToCaseEventData callBackEvent,
        String targetCaseCcdRef,
        boolean ignoreWarnings
    ) {
        CaseDetails targetCase = ccdApi.getCase(targetCaseCcdRef, callBackEvent.exceptionRecordJurisdiction);

        ServiceConfigItem serviceConfigItem = getServiceConfig(callBackEvent.service);
        return ccdCaseUpdater.updateCase(
            callBackEvent.exceptionRecord,
            serviceConfigItem.getService(),
            ignoreWarnings,
            callBackEvent.idamToken,
            callBackEvent.userId,
            targetCaseCcdRef,
            targetCase.getCaseTypeId()
        );
    }

    private Map<String, Object> buildCaseData(
        List<Map<String, Object>> exceptionDocuments,
        List<Map<String, Object>> existingDocuments
    ) {
        List<Object> documents = Documents.concatDocuments(exceptionDocuments, existingDocuments);
        return ImmutableMap.of(SCANNED_DOCUMENTS, documents, EVIDENCE_HANDLED, YesNoFieldValues.NO);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> attachExceptionRecordReference(
        List<Map<String, Object>> exceptionDocuments,
        Long exceptionRecordReference
    ) {
        List<String> exceptionDocumentsDcns = exceptionDocuments
            .stream()
            .map(Documents::getDocumentId)
            .collect(toList());
        log.info(
            "Attaching documents of {} exception record with following DCNs: {}",
            exceptionRecordReference,
            exceptionDocumentsDcns
        );

        return exceptionDocuments
            .stream()
            .map(document -> {
                HashMap<String, Object> copiedDocumentContent =
                    new HashMap<>((Map<String, Object>) document.get("value"));

                copiedDocumentContent.put(
                    "exceptionRecordReference",
                    String.valueOf(exceptionRecordReference)
                );

                return ImmutableMap.<String, Object>of("value", copiedDocumentContent);
            })
            .collect(toList());
    }

    private String createEventSummary(
        CaseDetails theCase,
        Long exceptionRecordId,
        List<Map<String, Object>> exceptionDocuments
    ) {
        return String.format(
            "Attaching exception record(%d) document numbers:%s to case:%d",
            exceptionRecordId,
            Documents.getDocumentNumbers(exceptionDocuments),
            theCase.getId()
        );
    }

    private ServiceConfigItem getServiceConfig(String service) {
        return Try.of(() -> serviceConfigProvider.getConfig(service))
            .filter(item -> !Strings.isNullOrEmpty(item.getUpdateUrl()))
            .getOrElseThrow(() -> new CallbackException("Update URL is not configured"));
    }
}
