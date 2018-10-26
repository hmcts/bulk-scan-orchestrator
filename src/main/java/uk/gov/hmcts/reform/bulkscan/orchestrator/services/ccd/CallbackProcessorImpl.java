package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.Value;
import io.vavr.control.Validation;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CallbackProcessorImpl.CallbackValidations.*;
import static uk.gov.hmcts.reform.ccd.client.model.CallbackTypes.ABOUT_TO_SUBMIT;

@Service
public class CallbackProcessorImpl implements CallbackProcessor {
    private static Logger log = LoggerFactory.getLogger(CallbackProcessor.class);

    @Override
    public List<String> process(String eventType, String eventId, CaseDetails caseDetails) {
        return Validation
            .combine(
                isAttachEvent(eventType),
                isAboutToSubmit(eventId),
                hasCaseDetails(caseDetails)
            )
            .ap((theType, anEventId, theCase) -> attach(theCase))
            .getOrElseGet(Value::toJavaList);
    }

    List<String> attach(CaseDetails caseDetails) {
        return emptyList();
    }

    interface CallbackValidations {

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


        static Validation<String, String> isAttachEvent(String type) {
            return "attach_case".equals(type)
                ? valid(type)
                : internalError("invalid type supplied: %s", type);
        }
    }

}
