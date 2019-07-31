package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;

final class CallbackValidations {

    private static final String CASE_TYPE_ID_SUFFIX = "_ExceptionRecord";

    private static final String SUPPLEMENTARY_EVIDENCE = "supplementary_evidence";
    private static final String EXCEPTION = "exception";
    private static final String ATTACH_TO_EXISTING_CASE = "attachToExistingCase";

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
    static Validation<String, String> hasJurisdiction(CaseDetails theCase) {
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
    static Validation<String, Void> hasValidCombinationOfEventIdAndClassification(
        CaseDetails theCase,
        String eventId
    ) {
        return getJourneyClassification(theCase)
            .map(classification -> {
                    switch (classification) {
                        case SUPPLEMENTARY_EVIDENCE:
                            return ATTACH_TO_EXISTING_CASE.equalsIgnoreCase(eventId)
                                ? Validation.<String, Void>valid(null)
                                : Validation.<String, Void>invalid(
                                format("The %s event is not supported for %s", eventId, classification
                                )
                            );
                        case EXCEPTION:
                            // When classification is exception and case data has ocr in it and
                            // if orchestrator url is configured for create case it will be invalid CCD configuration.
                            boolean isExceptionRecordWithOcr = exceptionRecordHasOcr(theCase);
                            return !isExceptionRecordWithOcr && ATTACH_TO_EXISTING_CASE.equalsIgnoreCase(eventId)
                                ? Validation.<String, Void>valid(null)
                                : Validation.<String, Void>invalid(
                                errorMessage(eventId, classification, isExceptionRecordWithOcr)
                            );
                        default:
                            return Validation.<String, Void>invalid(
                                format("Invalid journey classification %s", classification)
                            );
                    }
                }
            ).orElseGet(() -> invalid("No journey classification supplied"));
    }

    private static String errorMessage(String eventId, String classification, boolean isExceptionRecordWithOcr) {
        return isExceptionRecordWithOcr ? format(
            "The %s event is not supported for exception records with OCR",
            eventId
        ) : format("The %s event is not supported for %s or invalid CCD configuration", eventId, classification);
    }

    private static Optional<String> getJourneyClassification(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> data.get("journeyClassification"))
            .map(c -> (String) c);
    }

    private static boolean exceptionRecordHasOcr(CaseDetails theCase) {
        return Optional.ofNullable(theCase)
            .map(CaseDetails::getData)
            .map(data -> data.get("scanOCRData"))
            .isPresent();
    }
}
