package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Try;
import io.vavr.control.Validation;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.ServiceConfigItem;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues.YES;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.SUPPLEMENTARY_EVIDENCE_WITH_OCR;

@Component
public class CallbackValidator {
    private static final Logger log = LoggerFactory.getLogger(CallbackValidator.class);

    // todo review usage
    public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            // date/time
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            // optional offset
            .optionalStart().appendOffsetId()
            .toFormatter()
            .withZone(ZoneOffset.UTC);

    private static final String CASE_TYPE_ID_SUFFIX = "_ExceptionRecord";
    private static final String CLASSIFICATION_SUPPLEMENTARY_EVIDENCE = "SUPPLEMENTARY_EVIDENCE";
    private static final String CLASSIFICATION_SUPPLEMENTARY_EVIDENCE_WITH_OCR = "SUPPLEMENTARY_EVIDENCE_WITH_OCR";
    private static final String CLASSIFICATION_EXCEPTION = "EXCEPTION";

    private static final List<Classification> VALID_CLASSIFICATIONS_FOR_ATTACH_TO_CASE =
            asList(EXCEPTION, SUPPLEMENTARY_EVIDENCE, SUPPLEMENTARY_EVIDENCE_WITH_OCR);

    private final CaseReferenceValidator caseReferenceValidator;
    private final ScannedDocumentValidator scannedDocumentValidator;

    public CallbackValidator(
            CaseReferenceValidator caseReferenceValidator,
            ScannedDocumentValidator scannedDocumentValidator
    ) {
        this.caseReferenceValidator = caseReferenceValidator;
        this.scannedDocumentValidator = scannedDocumentValidator;
    }

    @Nonnull
    public Validation<String, String> hasCaseTypeId(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
                .map(CaseDetails::getCaseTypeId)
                .map(Validation::<String, String>valid)
                .orElse(invalid("Missing caseType"));
    }

    @Nonnull
    public Validation<String, String> hasFormType(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
                .map(CaseDetails::getData)
                .map(data -> (String) data.get("formType"))
                .map(Validation::<String, String>valid)
                .orElse(invalid("Missing Form Type"));
    }

    @Nonnull
    public Validation<String, String> hasJurisdiction(CaseDetails theCase) {
        String jurisdiction = null;
        return theCase != null
                && (jurisdiction = theCase.getJurisdiction()) != null
                ? valid(jurisdiction)
                : internalError("invalid jurisdiction supplied: %s", jurisdiction);
    }


    @Nonnull
    public Validation<String, String> hasSearchCaseReferenceType(CaseDetails theCase) {
        return caseReferenceValidator.validateCaseReferenceType(theCase);
    }

    @Nonnull
    public Validation<String, String> hasSearchCaseReference(CaseDetails theCase) {
        return caseReferenceValidator.validateSearchCaseReferenceWithSearchType(theCase);
    }

    @Nonnull
    public Validation<String, String> hasTargetCaseReference(CaseDetails theCase) {
        return caseReferenceValidator.validateTargetCaseReference(theCase);
    }

    @Nonnull
    public Validation<String, Long> hasAnId(CaseDetails theCase) {
        return theCase != null
                && theCase.getId() != null
                ? valid(theCase.getId())
                : invalid("Exception case has no Id");
    }

    @Nonnull
    public Validation<String, String> hasServiceNameInCaseTypeId(CaseDetails theCase) {
        return Optional
                .ofNullable(theCase)
                .map(CaseDetails::getCaseTypeId)
                .filter(Objects::nonNull)
                .map(caseTypeId -> {
                    if (caseTypeId.endsWith(CASE_TYPE_ID_SUFFIX)) {
                        String serviceName =
                                caseTypeId
                                        .replace(CASE_TYPE_ID_SUFFIX, "")
                                        .toLowerCase(Locale.getDefault());

                        if (!serviceName.isEmpty()) {
                            return Validation.<String, String>valid(serviceName);
                        }
                    }

                    return Validation.<String, String>invalid(
                            format("Case type ID (%s) has invalid format", caseTypeId)
                    );
                })
                .orElseGet(() -> invalid("No case type ID supplied"));
    }

    @Nonnull
    public Validation<String, List<Map<String, Object>>> hasAScannedRecord(CaseDetails theCase) {
        return scannedDocumentValidator.validate(theCase);
    }

    @Nonnull
    public Validation<String, Void> canBeAttachedToCase(CaseDetails theCase) {
        return getJourneyClassification(theCase)
                .map(
                    classification -> {
                        switch (classification) {
                            case CLASSIFICATION_SUPPLEMENTARY_EVIDENCE:
                                return Validation.<String, Void>valid(null);
                            case CLASSIFICATION_SUPPLEMENTARY_EVIDENCE_WITH_OCR:
                                return hasOcr(theCase)
                                        ? Validation.<String, Void>valid(null)
                                        : Validation.<String, Void>invalid(
                                        "The 'attach to case' event is not supported for supplementary evidence "
                                                + "with OCR but not containing OCR data"
                                );
                            case CLASSIFICATION_EXCEPTION:
                                return !hasOcr(theCase)
                                        ? Validation.<String, Void>valid(null)
                                        : Validation.<String, Void>invalid(
                                        "The 'attach to case' event is not supported for exception records with OCR"
                                );
                            default:
                                return Validation.<String, Void>invalid(
                                        format("Invalid journey classification %s", classification)
                                );
                        }
                    }
                ).orElseGet(() -> invalid("No journey classification supplied"));
    }


    @Nonnull
    public Validation<String, String> hasIdamToken(String idamToken) {
        return idamToken != null
                ? valid(idamToken)
                : invalid("Callback has no Idam token received in the header");
    }

    @Nonnull
    public Validation<String, String> hasUserId(String userId) {
        return userId != null
                ? valid(userId)
                : invalid("Callback has no user id received in the header");
    }

    public Validation<String, Void> validatePayments(
            CaseDetails theCase,
            Classification classification,
            ServiceConfigItem config
    ) {
        Optional<String> awaitingPaymentsOptional = getAwaitingPaymentDcnProcessing(theCase);

        if (awaitingPaymentsOptional.isPresent()
                && awaitingPaymentsOptional.get().equals(YES) // payments processing pending
                // check if config allows
                && !config.getAllowAttachingToCaseBeforePaymentsAreProcessedForClassifications()
                .contains(classification)
        ) {
            return invalid("Cannot attach this exception record to a case because it has pending payments");
        } else {
            return valid(null);
        }
    }

    public Validation<String, String> hasPoBox(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
                .map(CaseDetails::getData)
                .map(data -> data.get("poBox"))
                .map(o -> Validation.<String, String>valid((String) o))
                .orElse(invalid("Missing poBox"));
    }

    /**
     * Used in createCase callback only.
     * @param theCase from CCD
     * @return Validation of it
     */
    public Validation<String, Classification> hasJourneyClassification(CaseDetails theCase) {
        Optional<String> classificationOption = getJourneyClassification(theCase);

        return classificationOption
                .map(classification -> Try.of(() -> Classification.valueOf(classification)))
                .map(Try::toValidation)
                .map(validation -> validation
                        .mapError(throwable -> "Invalid journeyClassification. Error: " + throwable.getMessage())
                        .flatMap(classification -> validateClassification(classification, theCase))
                )
                .orElse(invalid("Missing journeyClassification"));
    }

    public Validation<String, Classification> hasJourneyClassificationForAttachToCase(CaseDetails theCase) {
        Optional<String> classificationOption = getJourneyClassification(theCase);

        return classificationOption
                .map(classification -> Try.of(() -> Classification.valueOf(classification)))
                .map(Try::toValidation)
                .map(validation -> validation
                        .mapError(throwable ->
                                format(
                                        "Journey Classification %s is not allowed "
                                                + "when attaching exception record to a case",
                                        classificationOption.get()
                                )
                        )
                        .flatMap(classification -> validateClassificationForAttachToCase(classification, theCase))
                )
                .orElse(invalid("Missing journeyClassification"));
    }

    public Validation<String, LocalDateTime> hasDateField(CaseDetails theCase, String dateField) {
        return Optional.ofNullable(theCase)
                .map(CaseDetails::getData)
                .map(data -> data.get(dateField))
                .map(o -> Validation.<String, LocalDateTime>valid(LocalDateTime.parse((String) o, FORMATTER)))
                .orElse(invalid("Missing " + dateField));
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Map<String, Object>>> getOcrData(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
                .map(CaseDetails::getData)
                .map(data -> (List<Map<String, Object>>) data.get("scanOCRData"));
    }

    /*
     * These errors created here are for errors not related to the user input. Hence putting internal in
     * front of the error so the user knows that they are not responsible and will not spend ages trying
     * to get it to work. I would suggest passing this via customer support people to verify that the strings
     * are good enough for the users and contain the right information to triage issues.
     */
    @Nonnull
    private <T> Validation<String, T> internalError(String error, T arg1) {
        log.error("{}:{}", error, arg1);
        String formatString = "Internal Error: " + error;
        return invalid(format(formatString, arg1));
    }

    private Optional<String> getJourneyClassification(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
                .map(CaseDetails::getData)
                .map(data -> data.get("journeyClassification"))
                .map(String.class::cast);
    }

    private Optional<String> getAwaitingPaymentDcnProcessing(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
                .map(CaseDetails::getData)
                .map(data -> data.get("awaitingPaymentDCNProcessing"))
                .map(String.class::cast);
    }

    private Validation<String, Classification> validateClassification(
            Classification classification,
            CaseDetails theCase
    ) {
        if (SUPPLEMENTARY_EVIDENCE.equals(classification)) {
            return invalid(format(
                    "Event createNewCase not allowed for the current journey classification %s",
                    classification
            ));
        }

        if ((EXCEPTION.equals(classification) || NEW_APPLICATION.equals(classification)) && !hasOcr(theCase)) {
            return invalid(format(
                    "Event createNewCase not allowed for the current journey classification %s without OCR",
                    classification
            ));
        }

        return valid(classification);
    }

    private boolean hasOcr(CaseDetails theCase) {
        return getOcrData(theCase)
                .map(list -> !CollectionUtils.isEmpty(list))
                .orElse(false);
    }

    private Validation<String, Classification> validateClassificationForAttachToCase(
            Classification classification,
            CaseDetails theCase
    ) {
        if (!VALID_CLASSIFICATIONS_FOR_ATTACH_TO_CASE.contains(classification)) {
            return invalid(format(
                    "The current Journey Classification %s is not allowed for attaching to case",
                    classification
            ));
        }

        if (SUPPLEMENTARY_EVIDENCE_WITH_OCR.equals(classification) && !hasOcr(theCase)) {
            return invalid(format(
                    "The current journey classification %s is not allowed without OCR data",
                    classification
            ));
        }

        return valid(classification);
    }
}
