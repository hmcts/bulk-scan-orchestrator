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
import static uk.gov.hmcts.reform.ccd.client.model.CallbackTypes.ABOUT_TO_SUBMIT;

interface CallbackValidations {
    Logger log = LoggerFactory.getLogger(CallbackProcessor.class);
    String ATTACH_TO_CASE_REFERENCE = "attachToCaseReference";

    static Validation<String, CaseDetails> hasCaseDetails(CaseDetails caseDetails) {
        return caseDetails != null
            ? valid(caseDetails)
            : internalError("no Case details supplied", null);
    }

    static Validation<String, String> isAboutToSubmit(String eventId) {
        return ABOUT_TO_SUBMIT.equals(eventId)
            ? valid(eventId)
            : internalError("event-id: %s invalid", eventId);
    }

    @NotNull
    static <T> Validation<String, T> internalError(String error, T arg1) {
        log.error("{}:{}", error, arg1);
        return invalid(format("Internal Error: " + error, arg1));
    }

    static Validation<String, String> hasJurisdiction(CaseDetails theCase) {
        return theCase.getJurisdiction() != null
            ? valid(theCase.getJurisdiction())
            : internalError("invalid jurisdiction supplied: %s", theCase.getJurisdiction());
    }

    static Validation<String, String> hasCaseReference(CaseDetails theCase) {
        Object caseReference;
        return theCase.getData() != null
            && (caseReference = theCase.getData().get(ATTACH_TO_CASE_REFERENCE)) != null
            && (caseReference instanceof String)
            ? valid((String) caseReference)
            : internalError("no case reference found: %s", String.valueOf(theCase.getData()));
    }

    static Validation<String, String> hasCaseTypeId(CaseDetails theCase) {
        String caseTypeId = theCase.getCaseTypeId();
        return !isNullOrEmpty(caseTypeId)
            ? valid(caseTypeId)
            : internalError("No caseType supplied: %s", caseTypeId);
    }

    static Validation<String, String> isAttachEvent(String type) {
        return "attach_case".equals(type)
            ? valid(type)
            : internalError("invalid type supplied: %s", type);
    }
}
