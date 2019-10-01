package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Try;
import io.vavr.control.Validation;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.EXCEPTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.SUPPLEMENTARY_EVIDENCE;

public final class CallbackValidations {

    private static final String CASE_TYPE_ID_SUFFIX = "_ExceptionRecord";

    private static final String CLASSIFICATION_SUPPLEMENTARY_EVIDENCE = "SUPPLEMENTARY_EVIDENCE";
    private static final String CLASSIFICATION_EXCEPTION = "EXCEPTION";

    private static final Logger log = LoggerFactory.getLogger(CallbackValidations.class);

    private static final CaseReferenceValidator caseRefValidator = new CaseReferenceValidator();
    private static final ScannedDocumentValidator scannedDocumentValidator = new ScannedDocumentValidator();

    private CallbackValidations() {
    }

    /*
     * These errors created here are for errors not related to the user input. Hence putting internal in
     * front of the error so the user knows that they are not responsible and will not spend ages trying
     * to get it to work. I would suggest passing this via customer support people to verify that the strings
     * are good enough for the users and contain the right information to triage issues.
     */
    @Nonnull
    private static <T> Validation<String, T> internalError(String error, T arg1) {
        log.error("{}:{}", error, arg1);
        String formatString = "Internal Error: " + error;
        return invalid(format(formatString, arg1));
    }

    @Nonnull
    public static Validation<String, String> hasCaseTypeId(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getCaseTypeId)
            .map(Validation::<String, String>valid)
            .orElse(invalid("Missing caseType"));
    }

    @Nonnull
    public static Validation<String, String> hasFormType(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> (String) data.get("formType"))
            .map(Validation::<String, String>valid)
            .orElse(invalid("Missing Form Type"));
    }

    @Nonnull
    public static Validation<String, String> hasJurisdiction(CaseDetails theCase) {
        String jurisdiction = null;
        return theCase != null
            && (jurisdiction = theCase.getJurisdiction()) != null
            ? valid(jurisdiction)
            : internalError("invalid jurisdiction supplied: %s", jurisdiction);
    }

    @Nonnull
    static Validation<String, String> hasSearchCaseReferenceType(CaseDetails theCase) {
        return caseRefValidator.validateCaseReferenceType(theCase);
    }

    @Nonnull
    static Validation<String, String> hasSearchCaseReference(CaseDetails theCase) {
        return caseRefValidator.validateSearchCaseReference(theCase);
    }

    @Nonnull
    static Validation<String, String> hasAttachToCaseReference(CaseDetails theCase) {
        return caseRefValidator.validateAttachToCaseReference(theCase);
    }

    @Nonnull
    static Validation<String, Long> hasAnId(CaseDetails theCase) {
        return theCase != null
            && theCase.getId() != null
            ? valid(theCase.getId())
            : invalid("Exception case has no Id");
    }

    @Nonnull
    static Validation<String, String> hasServiceNameInCaseTypeId(CaseDetails theCase) {
        return Optional
            .ofNullable(theCase)
            .map(CaseDetails::getCaseTypeId)
            .filter(caseTypeId -> caseTypeId != null)
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
    static Validation<String, List<Map<String, Object>>> hasAScannedRecord(CaseDetails theCase) {
        return scannedDocumentValidator.validate(theCase);
    }

    @Nonnull
    static Validation<String, Void> canBeAttachedToCase(CaseDetails theCase) {
        return getJourneyClassification(theCase)
            .map(
                classification -> {
                    switch (classification) {
                        case CLASSIFICATION_SUPPLEMENTARY_EVIDENCE:
                            return Validation.<String, Void>valid(null);
                        case CLASSIFICATION_EXCEPTION:
                            return !hasOcr(theCase)
                                ? Validation.<String, Void>valid(null)
                                : Validation.<String, Void>invalid(
                                    format("The 'attach to case' event is not supported for exception records with OCR")
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
    static Validation<String, String> hasIdamToken(String idamToken) {
        return idamToken != null
            ? valid(idamToken)
            : invalid("Callback has no Idam token received in the header");
    }

    @Nonnull
    static Validation<String, String> hasUserId(String userId) {
        return userId != null
            ? valid(userId)
            : invalid("Callback has no user id received in the header");
    }

    private static Optional<String> getJourneyClassification(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> data.get("journeyClassification"))
            .map(c -> (String) c);
    }

    static boolean hasOcr(CaseDetails theCase) {
        return getOcrData(theCase)
            .map(CollectionUtils::isNotEmpty)
            .orElse(false);
    }

    public static Validation<String, String> hasPoBox(CaseDetails theCase) {
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
    public static Validation<String, Classification> hasJourneyClassification(CaseDetails theCase) {
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

    private static Validation<String, Classification> validateClassification(
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

    public static Validation<String, Instant> hasDateField(CaseDetails theCase, String dateField) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> data.get(dateField))
            .map(o -> Validation.<String, Instant>valid(Instant.parse((String) o)))
            .orElse(invalid("Missing " + dateField));
    }

    @SuppressWarnings("unchecked")
    public static Optional<List<Map<String, Object>>> getOcrData(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> (List<Map<String, Object>>) data.get("scanOCRData"));
    }
}
