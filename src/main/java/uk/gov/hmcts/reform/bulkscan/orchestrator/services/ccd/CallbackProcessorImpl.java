package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.Value;
import io.vavr.control.Validation;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static uk.gov.hmcts.reform.ccd.client.model.CallbackTypes.ABOUT_TO_SUBMIT;

@Service
public class CallbackProcessorImpl implements CallbackProcessor {

    @Override
    public List<String> process(String type, String eventId, CaseDetails caseDetails) {
        return Validation
            .combine(
                isAttachEvent(type),
                isAboutToStart(eventId)
            )
            .ap((theType, anEventId) -> attach(caseDetails))
            .getOrElseGet(Value::toJavaList);
    }

    private List<String> attach(CaseDetails caseDetails) {
        return emptyList();
    }

    private Validation<String, String> isAboutToStart(String eventId) {
        return ABOUT_TO_SUBMIT.equals(eventId)
            ? valid(eventId)
            : internalError("event-id: %s invalid", eventId);
    }

    @NotNull
    private Validation<String, String> internalError(String error, String eventId) {
        return invalid(format("Internal Error: " + error, eventId));
    }

    private Validation<String, String> isAttachEvent(String type) {
        return "attach_case".equals(type)
            ? valid(type)
            : internalError("invalid type supplied: %s", type);
    }

}
