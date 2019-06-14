package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;

final class CallbackValidations {

    private static final String CASE_TYPE_ID_SUFFIX = "_ExceptionRecord";

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
    static Validation<String, String> hasIdamToken(String idamToken) {
        return idamToken != null
            ? valid(idamToken)
            : invalid("Callback has no user token received in the header");
    }

    @Nonnull
    static Validation<String, String> hasUserId(String userId) {
        return userId != null
            ? valid(userId)
            : invalid("Callback has no user id received in the header");
    }
}
