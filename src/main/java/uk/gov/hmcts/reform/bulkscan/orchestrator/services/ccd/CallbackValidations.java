package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;

final class CallbackValidations {
    private static final Logger log = LoggerFactory.getLogger(CallbackValidations.class);
    private static final String ATTACH_TO_CASE_REFERENCE = "attachToCaseReference";
    private static final String ATTACH_TO_EXISTING_CASE = "attachToExistingCase";

    @NotNull
    static Validation<String, CaseDetails> hasCaseDetails(CaseDetails caseDetails) {
        return caseDetails != null
            ? valid(caseDetails)
            : internalError("no case details supplied", null);
    }

    @NotNull
    static Validation<String, String> isAboutToSubmit(String eventId) {
        return ATTACH_TO_EXISTING_CASE.equals(eventId)
            ? valid(eventId)
            : internalError("event-id: %s invalid", eventId);
    }

    @NotNull
    static <T> Validation<String, T> internalError(String error, T arg1) {
        log.error("{}:{}", error, arg1);
        String formatString = "Internal Error: " + error;
        return invalid(format(formatString, arg1));
    }

    @NotNull
    static Validation<String, String> hasJurisdiction(CaseDetails theCase) {
        String jurisdiction = null;
        return theCase != null
            && (jurisdiction = theCase.getJurisdiction()) != null
            ? valid(jurisdiction)
            : internalError("invalid jurisdiction supplied: %s", jurisdiction);
    }

    @NotNull
    static Validation<String, String> hasCaseReference(CaseDetails theCase) {
        Object caseReference = null;
        return theCase != null
            && theCase.getData() != null
            && (caseReference = theCase.getData().get(ATTACH_TO_CASE_REFERENCE)) != null
            && (caseReference instanceof String)
            ? valid((String) caseReference)
            : internalError("no case reference found: %s", String.valueOf(caseReference));
    }

    @NotNull
    static Validation<String, String> hasCaseTypeId(CaseDetails theCase) {
        String caseTypeId = null;
        return theCase != null
            && !isNullOrEmpty(caseTypeId = theCase.getCaseTypeId())
            ? valid(caseTypeId)
            : internalError("No caseType supplied: %s", caseTypeId);
    }

    @NotNull
    static Validation<String, String> isAttachEvent(String type) {
        return "attach_case".equals(type)
            ? valid(type)
            : internalError("invalid type supplied: %s", type);
    }
}
