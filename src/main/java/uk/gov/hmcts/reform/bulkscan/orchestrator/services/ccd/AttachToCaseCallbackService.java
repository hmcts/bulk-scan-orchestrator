package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.collection.Array;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.in.CcdCallbackRequest;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.internal.ExceptionRecord;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.ExceptionRecordValidator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.config.ServiceConfigProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Map;

import static io.vavr.control.Validation.valid;
import static java.util.Collections.singletonList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.CaseReferenceTypes.CCD_CASE_REFERENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields.SEARCH_CASE_REFERENCE_TYPE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@Service
public class AttachToCaseCallbackService {

    private static final Logger log = LoggerFactory.getLogger(AttachToCaseCallbackService.class);

    private final ServiceConfigProvider serviceConfigProvider;
    private final ExceptionRecordValidator exceptionRecordValidator;
    private final ExceptionRecordFinalizer exceptionRecordFinalizer;
    private final ExceptionRecordAttacher exceptionRecordAttacher;
    private final CallbackValidator callbackValidator;
    private final EventIdValidator eventIdValidator;

    public AttachToCaseCallbackService(
            ServiceConfigProvider serviceConfigProvider,
            ExceptionRecordValidator exceptionRecordValidator,
            ExceptionRecordFinalizer exceptionRecordFinalizer,
            ExceptionRecordAttacher exceptionRecordAttacher,
            CallbackValidator callbackValidator,
            EventIdValidator eventIdValidator
    ) {
        this.serviceConfigProvider = serviceConfigProvider;
        this.exceptionRecordValidator = exceptionRecordValidator;
        this.exceptionRecordFinalizer = exceptionRecordFinalizer;
        this.exceptionRecordAttacher = exceptionRecordAttacher;
        this.callbackValidator = callbackValidator;
        this.eventIdValidator = eventIdValidator;
    }

    /**
     * Attaches exception record to a case.
     *
     * @return Either exception record field map, when processing was successful,
     *         or the list of errors, in case of errors
     */
    public Either<ErrorsAndWarnings, Map<String, Object>> process(
        CcdCallbackRequest request,
        String requesterIdamToken,
        String requesterUserId
    ) {
        Validation<String, Void> canAccess = exceptionRecordValidator.mandatoryPrerequisites(
            () -> eventIdValidator.isAttachToCaseEvent(request.getEventId()),
            () -> callbackValidator.canBeAttachedToCase(request.getCaseDetails()),
            () -> callbackValidator.hasIdamToken(requesterIdamToken).map(item -> null),
            () -> callbackValidator.hasUserId(requesterUserId).map(item -> null)
        );

        if (canAccess.isInvalid()) {
            log.warn("Validation error: {}", canAccess.getError());

            return Either.left(ErrorsAndWarnings.withErrors(singletonList(canAccess.getError())));
        }

        return getValidation(request.getCaseDetails(), requesterIdamToken, requesterUserId)
            .map(callBackEvent ->
                exceptionRecordAttacher.tryAttachToCase(
                        callBackEvent,
                        request.getCaseDetails(),
                        request.isIgnoreWarnings()
                )
            )
            .map(errorsOrRef -> errorsOrRef.map(caseRef -> finalizeExceptionRecData(request.getCaseDetails(), caseRef)))
            .getOrElseGet(errors -> Either.left(ErrorsAndWarnings.withErrors(errors.toJavaList())));
    }

    private Map<String, Object> finalizeExceptionRecData(CaseDetails exceptionRec, String caseRef) {
        return exceptionRecordFinalizer
            .finalizeExceptionRecord(
                exceptionRec.getData(),
                caseRef,
                CcdCallbackType.ATTACHING_SUPPLEMENTARY_EVIDENCE
            );
    }

    private Validation<Seq<String>, AttachToCaseEventData> getValidation(
        CaseDetails exceptionRecord,
        String requesterIdamToken,
        String requesterUserId
    ) {
        boolean useSearchCaseReference = exceptionRecord.getData() != null
            && exceptionRecord.getData().get(SEARCH_CASE_REFERENCE_TYPE) != null;

        Validation<String, String> caseReferenceTypeValidation = useSearchCaseReference
            ? callbackValidator.hasSearchCaseReferenceType(exceptionRecord)
            : valid(CCD_CASE_REFERENCE);

        Validation<String, String> caseReferenceValidation = useSearchCaseReference
            ? callbackValidator.hasSearchCaseReference(exceptionRecord)
            : callbackValidator.hasTargetCaseReference(exceptionRecord);

        Validation<String, String> jurisdictionValidation = callbackValidator.hasJurisdiction(exceptionRecord);
        Validation<String, String> serviceNameInCaseTypeIdValidation =
                callbackValidator.hasServiceNameInCaseTypeId(exceptionRecord);
        Validation<String, Long> idValidation = callbackValidator.hasAnId(exceptionRecord);
        Validation<String, List<Map<String, Object>>> scannedRecordValidation =
                callbackValidator.hasAScannedRecord(exceptionRecord);
        Validation<String, String> idamTokenValidation = callbackValidator.hasIdamToken(requesterIdamToken);
        Validation<String, String> userIdValidation = callbackValidator.hasUserId(requesterUserId);
        Validation<String, Classification> classificationValidation =
            callbackValidator.hasJourneyClassificationForAttachToCase(exceptionRecord);

        final Validation<String, Void> paymentsValidation;
        if (classificationValidation.isValid() && serviceNameInCaseTypeIdValidation.isValid()) {
            ServiceConfigItem serviceConfig = serviceConfigProvider.getConfig(
                serviceNameInCaseTypeIdValidation.get()
            );
            paymentsValidation =
                    callbackValidator.validatePayments(exceptionRecord, classificationValidation.get(), serviceConfig);
        } else {
            paymentsValidation = Validation.valid(null);
        }

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
            classificationValidation,
            paymentsValidation
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
}
