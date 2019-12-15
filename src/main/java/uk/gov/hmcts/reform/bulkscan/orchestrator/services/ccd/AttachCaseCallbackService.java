package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.model.request.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.CallbackException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ExceptionRecordValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public Either<List<String>, Map<String, Object>> process(
        CaseDetails exceptionRecordData,
        String requesterIdamToken,
        String requesterUserId,
        String eventId,
        Boolean ignoreWarnings
    ) {
        Validation<String, Void> eventIdValidation = isAttachToCaseEvent(eventId);

        if (eventIdValidation.isInvalid()) {
            String eventIdValidationError = eventIdValidation.getError();
            log.warn("Validation error {}", eventIdValidationError);
            return Either.left(singletonList(eventIdValidationError));
        }

        Validation<String, Void> classificationValidation = canBeAttachedToCase(exceptionRecordData);

        if (classificationValidation.isInvalid()) {
            String eventIdClassificationValidationError = classificationValidation.getError();
            log.warn("Validation error {}", eventIdClassificationValidationError);
            return Either.left(singletonList(eventIdClassificationValidationError));
        }
        boolean useSearchCaseReference = isSearchCaseReferenceTypePresent(exceptionRecordData);

        return getValidation(exceptionRecordData, useSearchCaseReference, requesterIdamToken, requesterUserId)
            .map(event -> tryAttachToCase(event, exceptionRecordData, ignoreWarnings))
            .map(attachCaseResult ->
                attachCaseResult.map(modifiedFields -> mergeCaseFields(exceptionRecordData.getData(), modifiedFields))
            )
            .getOrElseGet(errors -> Either.left(errors.toJavaList()));
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

        return Validation
            .combine(
                hasJurisdiction(exceptionRecord),
                hasServiceNameInCaseTypeId(exceptionRecord),
                caseReferenceTypeValidation,
                caseReferenceValidation,
                hasAnId(exceptionRecord),
                hasAScannedRecord(exceptionRecord),
                hasIdamToken(requesterIdamToken),
                hasUserId(requesterUserId)
            )
            .ap(AttachToCaseEventData::new);
    }

    //The code below need to be rewritten to reuse the EventPublisher class

    /**
     * Attaches exception record to a case.
     *
     * @return Either a map of fields that should be modified in CCD when processing was successful,
     *         or the list of errors, in case of errors
     */
    private Either<List<String>, Map<String, Object>> tryAttachToCase(
        AttachToCaseEventData event,
        CaseDetails exceptionRecordData,
        boolean ignoreWarnings
    ) {
        try {
            log.info(
                "Attaching exception record '{}' to a case by reference type '{}' and reference '{}'",
                event.exceptionRecordId,
                event.targetCaseRefType,
                event.targetCaseRef
            );

            Map<String, Object> result = attachToCase(event, exceptionRecordData, ignoreWarnings);
            log.info(
                "Successfully attached exception record {} to case {}",
                event.exceptionRecordId,
                event.targetCaseRef
            );
            return Either.right(result);

        } catch (AlreadyAttachedToCaseException
            | DuplicateDocsException
            | CaseNotFoundException
            | MultipleCasesFoundException
            | InvalidCaseIdException exc
        ) {
            log.warn(
                "Validation error when attaching ER {} in {} to case {}",
                event.exceptionRecordId,
                event.exceptionRecordJurisdiction,
                event.targetCaseRef,
                exc
            );
            return Either.left(singletonList(exc.getMessage()));

        } catch (PaymentsPublishingException exception) {
            log.error(
                "Failed to send update to payment processor for {} exception record {}",
                event.exceptionRecordJurisdiction,
                exceptionRecordData.getId(),
                exception
            );
            return Either.left(singletonList(PAYMENT_ERROR_MSG));
        } catch (Exception exc) {
            log.error(
                "Error attaching ER {} in {} to case {}",
                event.exceptionRecordId,
                event.exceptionRecordJurisdiction,
                event.targetCaseRef,
                exc
            );
            throw exc;
        }
    }

    private Map<String, Object> attachToCase(
        AttachToCaseEventData event,
        CaseDetails exceptionRecordData,
        boolean ignoreWarnings
    ) {
        String targetCaseCcdId;

        if (EXTERNAL_CASE_REFERENCE.equals(event.targetCaseRefType)) {
            targetCaseCcdId = attachCaseByLegacyId(event, exceptionRecordData, ignoreWarnings);
        } else {
            attachCaseByCcdId(
                event.exceptionRecordJurisdiction,
                event.targetCaseRef,
                event.exceptionRecordDocuments,
                event.exceptionRecordId,
                event.idamToken,
                event.userId,
                exceptionRecordData,
                ignoreWarnings
            );

            targetCaseCcdId = event.targetCaseRef;
        }

        return ImmutableMap.of(ATTACH_TO_CASE_REFERENCE, targetCaseCcdId);
    }

    private String attachCaseByLegacyId(
        AttachToCaseEventData event,
        CaseDetails exceptionRecordData,
        boolean ignoreWarnings
    ) {
        List<Long> targetCaseCcdIds = ccdApi.getCaseRefsByLegacyId(event.targetCaseRef, event.service);

        if (targetCaseCcdIds.size() == 1) {
            String targetCaseCcdId = targetCaseCcdIds.get(0).toString();

            log.info(
                "Found case with CCD ID '{}' for legacy ID '{}' (attaching exception record '{}')",
                targetCaseCcdId,
                event.targetCaseRef,
                event.exceptionRecordId
            );

            attachCaseByCcdId(
                event.exceptionRecordJurisdiction,
                targetCaseCcdId,
                event.exceptionRecordDocuments,
                event.exceptionRecordId,
                event.idamToken,
                event.userId,
                exceptionRecordData,
                ignoreWarnings
            );

            return targetCaseCcdId;
        } else if (targetCaseCcdIds.isEmpty()) {
            throw new CaseNotFoundException(
                String.format("No case found for legacy case reference %s", event.targetCaseRef)
            );
        } else {
            throw new MultipleCasesFoundException(
                String.format(
                    "Multiple cases (%s) found for the given legacy case reference: %s",
                    targetCaseCcdIds.stream().map(String::valueOf).collect(joining(", ")),
                    event.targetCaseRef
                )
            );
        }
    }

    private void attachCaseByCcdId(
        String exceptionRecordJurisdiction,
        String targetCaseCcdRef,
        List<Map<String, Object>> exceptionRecordDocuments,
        Long exceptionRecordId,
        String idamToken,
        String userId,
        CaseDetails exceptionRecordData,
        boolean ignoreWarnings
    ) {
        log.info("Attaching exception record '{}' to a case by CCD ID '{}'", exceptionRecordId, targetCaseCcdRef);

        Validation<String, Classification> classificationValidation =
            hasJourneyClassificationForAttachToCase(exceptionRecordData);
        if (classificationValidation.isValid()) {
            if (SUPPLEMENTARY_EVIDENCE_WITH_OCR == classificationValidation.get()) {
                ServiceConfigItem serviceConfigItem = getServiceConfig(exceptionRecordData);
                Validation<Seq<String>, ExceptionRecord> exceptionRecordValidation =
                    exceptionRecordValidator.getValidation(exceptionRecordData);
                if (exceptionRecordValidation.isValid()) {
                    ccdCaseUpdater.updateCase(
                        exceptionRecordValidation.get(),
                        serviceConfigItem,
                        ignoreWarnings,
                        idamToken,
                        userId,
                        targetCaseCcdRef
                    );
                } else {
                    throw new CallbackException(exceptionRecordValidation.getError().mkString());
                }
            } else {
                verifyExceptionRecordIsNotAttachedToCase(exceptionRecordJurisdiction, exceptionRecordId);

                CaseDetails theCase = ccdApi.getCase(targetCaseCcdRef, exceptionRecordJurisdiction);
                List<Map<String, Object>> targetCaseDocuments = getScannedDocuments(theCase);

                //This is done so exception record does not change state if there is a document error
                checkForDuplicatesOrElse(
                    exceptionRecordDocuments,
                    targetCaseDocuments,
                    ids -> throwDuplicateError(targetCaseCcdRef, ids)
                );

                List<Map<String, Object>> newCaseDocuments = attachExceptionRecordReference(
                    exceptionRecordDocuments,
                    exceptionRecordId
                );

                StartEventResponse event = ccdApi.startAttachScannedDocs(theCase, idamToken, userId);

                Map<String, Object> newCaseData = buildCaseData(newCaseDocuments, targetCaseDocuments);

                ccdApi.attachExceptionRecord(
                    theCase,
                    idamToken,
                    userId,
                    newCaseData,
                    createEventSummary(theCase, exceptionRecordId, newCaseDocuments),
                    event
                );

                log.info(
                    "Successfully attach Exception Record with ID {} to case with id {}",
                    theCase.getId(),
                    exceptionRecordData.getId()
                );

                paymentsProcessor.updatePayments(exceptionRecordData, theCase.getId());
            }
        } else {
            throw new CallbackException(classificationValidation.getError());
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

        if (attachToCaseRef != null && Strings.isNotEmpty(attachToCaseRef.toString())) {
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

        public AttachToCaseEventData(
            String exceptionRecordJurisdiction,
            String service,
            String targetCaseRefType,
            String targetCaseRef,
            Long exceptionRecordId,
            List<Map<String, Object>> exceptionRecordDocuments,
            String idamToken,
            String userId
        ) {
            this.exceptionRecordJurisdiction = exceptionRecordJurisdiction;
            this.service = service;
            this.targetCaseRefType = targetCaseRefType;
            this.targetCaseRef = targetCaseRef;
            this.exceptionRecordId = exceptionRecordId;
            this.exceptionRecordDocuments = exceptionRecordDocuments;
            this.idamToken = idamToken;
            this.userId = userId;
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
            .filter(item -> !com.google.common.base.Strings.isNullOrEmpty(item.getUpdateUrl()))
            .getOrElseThrow(() -> new CallbackException("Update URL is not configured")).get();
    }
}
