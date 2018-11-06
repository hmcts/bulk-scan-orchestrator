package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;
import static uk.gov.hmcts.reform.ccd.client.model.CallbackTypes.ABOUT_TO_SUBMIT;

final class CallbackValidations {
    private static Logger log = LoggerFactory.getLogger(CallbackValidations.class);

    private CallbackValidations() {
    }

    static Validation<String, CaseDetails> hasCaseDetails(CaseDetails caseDetails) {
        return caseDetails != null
            ? valid(caseDetails)
            : internalError("no Case details supplied", null);
    }

    @NotNull
    static Validation<String, String> isAboutToSubmit(String eventId) {
        return ABOUT_TO_SUBMIT.equals(eventId)
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
    static Validation<String, String> isAttachEvent(String type) {
        return "attach_case".equals(type)
            ? valid(type)
            : internalError("invalid type supplied: %s", type);
    }
}
