package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.vavr.collection.Array;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ExceptionRecordValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ProcessResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.vavr.control.Validation.valid;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.canBeAttachedToCase;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasAScannedRecord;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasAnId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasAttachToCaseReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasIdamToken;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJourneyClassificationForAttachToCase;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasJurisdiction;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasSearchCaseReference;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasSearchCaseReferenceType;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasServiceNameInCaseTypeId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackValidations.hasUserId;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.checkForDuplicatesOrElse;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.concatDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.getDocumentNumbers;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.Documents.getScannedDocuments;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.EventIdValidator.isAttachToCaseEvent;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes.CCD_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes.EXTERNAL_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.ATTACH_TO_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.EVIDENCE_HANDLED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SCANNED_DOCUMENTS;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE_TYPE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@Service
public class AttachCaseCallbackService {

    public static final String PAYMENT_ERROR_MSG = "Payment references cannot be processed. Please try again later";

    private static final Logger log = LoggerFactory.getLogger(AttachCaseCallbackService.class);

    private final ServiceConfigProvider serviceConfigProvider;

    private final CcdApi ccdApi;

    private final ExceptionRecordValidator exceptionRecordValidator;

    private final CcdCaseUpdater ccdCaseUpdater;

    private final PaymentsProcessor paymentsProcessor;

    public AttachCaseCallbackService(ServiceConfigProvider serviceConfigProvider,
                                     CcdApi ccdApi,
                                     ExceptionRecordValidator exceptionRecordValidator,
                                     CcdCaseUpdater ccdCaseUpdater,
                                     PaymentsProcessor paymentsProcessor
    ) {
        this.serviceConfigProvider = serviceConfigProvider;
        this.ccdApi = ccdApi;
        this.exceptionRecordValidator = exceptionRecordValidator;
        this.paymentsProcessor = paymentsProcessor;
        this.ccdCaseUpdater = ccdCaseUpdater;
    }

    /**
     * Attaches exception record to a case.
     *
     * @return Either exception record field map, when processing was successful,
     *         or the list of errors, in case of errors
     */
    public Either<ErrorsAndWarnings, Map<String, Object>> process(
        CaseDetails exceptionRecordDetails,
        String requesterIdamToken,
        String requesterUserId,
        String eventId,
        Boolean ignoreWarnings
    ) {
        Validation<String, Void> eventIdValidation = isAttachToCaseEvent(eventId);

        if (eventIdValidation.isInvalid()) {
            String eventIdValidationError = eventIdValidation.getError();
            log.warn("Validation error {}", eventIdValidationError);
            return Either.left(ErrorsAndWarnings.withErrors(singletonList(eventIdValidationError)));
        }

        Validation<String, Void> classificationValidation = canBeAttachedToCase(exceptionRecordDetails);

        if (classificationValidation.isInvalid()) {
            String eventIdClassificationValidationError = classificationValidation.getError();
            log.warn("Validation error {}", eventIdClassificationValidationError);
            return Either.left(ErrorsAndWarnings.withErrors(singletonList(eventIdClassificationValidationError)));
        }
        boolean useSearchCaseReference = isSearchCaseReferenceTypePresent(exceptionRecordDetails);

        return getValidation(exceptionRecordDetails, useSearchCaseReference, requesterIdamToken, requesterUserId)
            .map(callBackEvent -> tryAttachToCase(callBackEvent, exceptionRecordDetails, ignoreWarnings))
            .map(attachCaseResult ->
                attachCaseResult.map(modifiedFields ->
                    mergeCaseFields(exceptionRecordDetails.getData(), modifiedFields))
            )
            .getOrElseGet(errors -> Either.left(ErrorsAndWarnings.withErrors(errors.toJavaList())));
    }

    private Validation<Seq<String>, AttachToCaseEventData> getValidation(
        CaseDetails exceptionRecord,
        boolean useSearchCaseReference,
        String requesterIdamToken,
        String requesterUserId
    ) {
        Validation<String, String> caseReferenceTypeValidation = useSearchCaseReference
            ? hasSearchCaseReferenceType(exceptionRecord)
            : valid(CCD_CASE_REFERENCE);

        Validation<String, String> caseReferenceValidation = useSearchCaseReference
            ? hasSearchCaseReference(exceptionRecord)
            : hasAttachToCaseReference(exceptionRecord);

        Validation<String, String> jurisdictionValidation = hasJurisdiction(exceptionRecord);
        Validation<String, String> serviceNameInCaseTypeIdValidation = hasServiceNameInCaseTypeId(exceptionRecord);
        Validation<String, Long> idValidation = hasAnId(exceptionRecord);
        Validation<String, List<Map<String, Object>>> scannedRecordValidation = hasAScannedRecord(exceptionRecord);
        Validation<String, String> idamTokenValidation = hasIdamToken(requesterIdamToken);
        Validation<String, String> userIdValidation = hasUserId(requesterUserId);
        Validation<String, Classification> classificationValidation =
            hasJourneyClassificationForAttachToCase(exceptionRecord);

        final Validation<Seq<String>, ExceptionRecord> exceptionRecordValidation;
        if (classificationValidation.isValid() && classificationValidation.get() == SUPPLEMENTARY_EVIDENCE_WITH_OCR) {
            exceptionRecordValidation = exceptionRecordValidator.getValidation(exceptionRecord);
        } else {
            // exceptionRecord value is used only for SUPPLEMENTARY_EVIDENCE_WITH_OCR journey classification
            // otherwise we can safely set its value to null in the validation result
            exceptionRecordValidation = Validation.valid(null);
        }

        Seq<Validation<String, ?>> validations = Array.of(
            jurisdictionValidation,
            serviceNameInCaseTypeIdValidation,
            caseReferenceTypeValidation,
            caseReferenceValidation,
            idValidation,
            scannedRecordValidation,
            idamTokenValidation,
            userIdValidation,
            classificationValidation
        );

        Seq<String> errors = getValidationErrors(validations);
        if (errors.isEmpty() && exceptionRecordValidation.isValid()) {
            return Validation.valid(new AttachToCaseEventData(
                jurisdictionValidation.get(),
                serviceNameInCaseTypeIdValidation.get(),
                caseReferenceTypeValidation.get(),
                caseReferenceValidation.get(),
                idValidation.get(),
                scannedRecordValidation.get(),
                idamTokenValidation.get(),
                userIdValidation.get(),
                classificationValidation.get(),
                exceptionRecordValidation.get()
            ));
        } else if (exceptionRecordValidation.isInvalid()) {
            return Validation.invalid(errors.appendAll(exceptionRecordValidation.getError()));
        } else {
            return Validation.invalid(errors);
        }
    }

    private Seq<String> getValidationErrors(Seq<Validation<String, ?>> validations) {
        return validations
            .filter(Validation::isInvalid)
            .map(Validation::getError);
    }

    //The code below need to be rewritten to reuse the EventPublisher class

    /**
     * Attaches exception record to a case.
     *
     * @return Either a map of fields that should be modified in CCD when processing was successful,
     *         or the list of errors, in case of errors
     */
    private Either<ErrorsAndWarnings, Map<String, Object>> tryAttachToCase(
        AttachToCaseEventData callBackEvent,
        CaseDetails exceptionRecordDetails,
        boolean ignoreWarnings
    ) {
        try {
            log.info(
                "Attaching exception record '{}' to a case by reference type '{}' and reference '{}'",
                callBackEvent.exceptionRecordId,
                callBackEvent.targetCaseRefType,
                callBackEvent.targetCaseRef
            );

            return attachToCase(callBackEvent, exceptionRecordDetails, ignoreWarnings);
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
                exceptionRecordDetails.getId(),
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

    private Either<ErrorsAndWarnings, Map<String, Object>> attachToCase(
        AttachToCaseEventData callBackEvent,
        CaseDetails exceptionRecordDetails,
        boolean ignoreWarnings
    ) {
        String targetCaseCcdId;

        if (EXTERNAL_CASE_REFERENCE.equals(callBackEvent.targetCaseRefType)) {
            targetCaseCcdId = attachCaseByLegacyId(callBackEvent, exceptionRecordDetails, ignoreWarnings);
        } else {
            Optional<ErrorsAndWarnings> attachResult = attachCaseByCcdId(
                callBackEvent,
                callBackEvent.targetCaseRef,
                exceptionRecordDetails,
                ignoreWarnings
            );

            if (attachResult.isPresent()) {
                return Either.left(attachResult.get());
            }

            targetCaseCcdId = callBackEvent.targetCaseRef;
        }

        log.info(
            "Successfully attached exception record {} to case {}",
            callBackEvent.exceptionRecordId,
            callBackEvent.targetCaseRef
        );

        return Either.right(ImmutableMap.of(ATTACH_TO_CASE_REFERENCE, targetCaseCcdId));
    }

    private String attachCaseByLegacyId(
        AttachToCaseEventData callBackEvent,
        CaseDetails exceptionRecordDetails,
        boolean ignoreWarnings
    ) {
        List<Long> targetCaseCcdIds = ccdApi.getCaseRefsByLegacyId(callBackEvent.targetCaseRef, callBackEvent.service);

        if (targetCaseCcdIds.size() == 1) {
            String targetCaseCcdId = targetCaseCcdIds.get(0).toString();

            log.info(
                "Found case with CCD ID '{}' for legacy ID '{}' (attaching exception record '{}')",
                targetCaseCcdId,
                callBackEvent.targetCaseRef,
                callBackEvent.exceptionRecordId
            );

            attachCaseByCcdId(
                callBackEvent,
                targetCaseCcdId,
                exceptionRecordDetails,
                ignoreWarnings
            );

            return targetCaseCcdId;
        } else if (targetCaseCcdIds.isEmpty()) {
            throw new CaseNotFoundException(
                String.format("No case found for legacy case reference %s", callBackEvent.targetCaseRef)
            );
        } else {
            throw new MultipleCasesFoundException(
                String.format(
                    "Multiple cases (%s) found for the given legacy case reference: %s",
                    targetCaseCcdIds.stream().map(String::valueOf).collect(joining(", ")),
                    callBackEvent.targetCaseRef
                )
            );
        }
    }

    private Optional<ErrorsAndWarnings> attachCaseByCcdId(
        AttachToCaseEventData callBackEvent,
        String targetCaseCcdRef,
        CaseDetails exceptionRecordDetails,
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
                    targetCaseCcdRef,
                    exceptionRecordDetails
                );
                return Optional.empty();

            case SUPPLEMENTARY_EVIDENCE_WITH_OCR:
                return updateSupplementaryEvidenceWithOcr(
                    callBackEvent,
                    targetCaseCcdRef,
                    exceptionRecordDetails,
                    ignoreWarnings
                );

            default:
                throw new CallbackException("Invalid Journey Classification: " + callBackEvent.classification);
        }
    }

    private void updateSupplementaryEvidence(
        AttachToCaseEventData callBackEvent,
        String targetCaseCcdRef,
        CaseDetails exceptionRecordDetails
    ) {
        verifyExceptionRecordIsNotAttachedToCase(
            callBackEvent.exceptionRecordJurisdiction,
            callBackEvent.exceptionRecordId
        );

        CaseDetails theCase = ccdApi.getCase(targetCaseCcdRef, callBackEvent.exceptionRecordJurisdiction);
        List<Map<String, Object>> targetCaseDocuments = getScannedDocuments(theCase);

        //This is done so exception record does not change state if there is a document error
        checkForDuplicatesOrElse(
            callBackEvent.exceptionRecordDocuments,
            targetCaseDocuments,
            ids -> throwDuplicateError(targetCaseCcdRef, ids)
        );

        List<Map<String, Object>> newCaseDocuments = attachExceptionRecordReference(
            callBackEvent.exceptionRecordDocuments,
            callBackEvent.exceptionRecordId
        );

        StartEventResponse ccdStartEvent =
            ccdApi.startAttachScannedDocs(theCase, callBackEvent.idamToken, callBackEvent.userId);

        Map<String, Object> newCaseData = buildCaseData(newCaseDocuments, targetCaseDocuments);

        ccdApi.attachExceptionRecord(
            theCase,
            callBackEvent.idamToken,
            callBackEvent.userId,
            newCaseData,
            createEventSummary(theCase, callBackEvent.exceptionRecordId, newCaseDocuments),
            ccdStartEvent
        );

        log.info(
            "Successfully attach Exception Record with ID {} to case with id {}",
            theCase.getId(),
            exceptionRecordDetails.getId()
        );

        paymentsProcessor.updatePayments(exceptionRecordDetails, theCase.getId());
    }

    private Optional<ErrorsAndWarnings> updateSupplementaryEvidenceWithOcr(
        AttachToCaseEventData callBackEvent,
        String targetCaseCcdRef,
        CaseDetails exceptionRecordDetails,
        boolean ignoreWarnings
    ) {

        verifyExceptionRecordIsNotAttachedToCase(
            callBackEvent.exceptionRecordJurisdiction,
            callBackEvent.exceptionRecordId
        );

        ServiceConfigItem serviceConfigItem = getServiceConfig(exceptionRecordDetails);
        ProcessResult processResult = ccdCaseUpdater.updateCase(
            callBackEvent.exceptionRecord,
            serviceConfigItem,
            ignoreWarnings,
            callBackEvent.idamToken,
            callBackEvent.userId,
            targetCaseCcdRef
        );

        if (!processResult.getErrors().isEmpty() || !processResult.getWarnings().isEmpty()) {
            return Optional.of(ErrorsAndWarnings.withErrorsAndWarnings(
                processResult.getErrors(),
                processResult.getWarnings()
            ));
        } else {
            return Optional.empty();
        }
    }

    private Map<String, Object> buildCaseData(
        List<Map<String, Object>> exceptionDocuments,
        List<Map<String, Object>> existingDocuments
    ) {
        List<Object> documents = concatDocuments(exceptionDocuments, existingDocuments);
        return ImmutableMap.of(SCANNED_DOCUMENTS, documents, EVIDENCE_HANDLED, YesNoFieldValues.NO);
    }

    private void verifyExceptionRecordIsNotAttachedToCase(
        String exceptionRecordJurisdiction,
        Long exceptionRecordReference
    ) {
        CaseDetails fetchedExceptionRecord = ccdApi.getCase(
            exceptionRecordReference.toString(),
            exceptionRecordJurisdiction
        );

        Object attachToCaseRef = fetchedExceptionRecord.getData().get(ATTACH_TO_CASE_REFERENCE);

        if (attachToCaseRef != null && !Strings.isNullOrEmpty(attachToCaseRef.toString())) {
            throw new AlreadyAttachedToCaseException("Exception record is already attached to case " + attachToCaseRef);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> attachExceptionRecordReference(
        List<Map<String, Object>> exceptionDocuments,
        Long exceptionRecordReference
    ) {
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

    private void throwDuplicateError(String caseRef, Set<String> duplicateIds) {
        throw new DuplicateDocsException(
            String.format(
                "Document(s) with control number %s are already attached to case reference: %s",
                duplicateIds,
                caseRef
            )
        );
    }

    private String createEventSummary(
        CaseDetails theCase,
        Long exceptionRecordId,
        List<Map<String, Object>> exceptionDocuments
    ) {
        return String.format(
            "Attaching exception record(%d) document numbers:%s to case:%d",
            exceptionRecordId,
            getDocumentNumbers(exceptionDocuments),
            theCase.getId()
        );
    }

    private boolean isSearchCaseReferenceTypePresent(CaseDetails exceptionRecord) {
        return exceptionRecord.getData() != null
            && exceptionRecord.getData().get(SEARCH_CASE_REFERENCE_TYPE) != null;
    }

    /**
     * Information received in the callback call for the attach event.
     */
    private static class AttachToCaseEventData {
        public final String exceptionRecordJurisdiction;
        public final String service;
        public final String targetCaseRef;
        public final String targetCaseRefType;
        public final Long exceptionRecordId;
        public final List<Map<String, Object>> exceptionRecordDocuments;
        public final String idamToken;
        public final String userId;
        public final Classification classification;
        public final ExceptionRecord exceptionRecord;

        public AttachToCaseEventData(
            String exceptionRecordJurisdiction,
            String service,
            String targetCaseRefType,
            String targetCaseRef,
            Long exceptionRecordId,
            List<Map<String, Object>> exceptionRecordDocuments,
            String idamToken,
            String userId,
            Classification classification,
            ExceptionRecord exceptionRecord
        ) {
            this.exceptionRecordJurisdiction = exceptionRecordJurisdiction;
            this.service = service;
            this.targetCaseRefType = targetCaseRefType;
            this.targetCaseRef = targetCaseRef;
            this.exceptionRecordId = exceptionRecordId;
            this.exceptionRecordDocuments = exceptionRecordDocuments;
            this.idamToken = idamToken;
            this.userId = userId;
            this.classification = classification;
            this.exceptionRecord = exceptionRecord;
        }
    }

    private Map<String, Object> mergeCaseFields(
        Map<String, Object> originalFields,
        Map<String, Object> modifiedFields
    ) {
        Map<String, Object> merged = new HashMap<>(
            Maps.difference(originalFields, modifiedFields).entriesOnlyOnLeft()
        );

        merged.putAll(modifiedFields);
        return merged;
    }

    private ServiceConfigItem getServiceConfig(CaseDetails caseDetails) {
        return hasServiceNameInCaseTypeId(caseDetails).flatMap(service -> Try
            .of(() -> serviceConfigProvider.getConfig(service))
            .toValidation()
            .mapError(Throwable::getMessage)
        )
            .filter(item -> !Strings.isNullOrEmpty(item.getUpdateUrl()))
            .getOrElseThrow(() -> new CallbackException("Update URL is not configured")).get();
    }
}
