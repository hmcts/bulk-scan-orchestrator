package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;

@Component
public class CallbackValidator {
    private static final Logger log = LoggerFactory.getLogger(CallbackValidator.class);

    private static final String CASE_TYPE_ID_SUFFIX = "_ExceptionRecord";

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
    public Validation<String, List<Map<String, Object>>> hasAScannedRecord(CaseDetails theCase) {
        return scannedDocumentValidator.validate(theCase);
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
}
