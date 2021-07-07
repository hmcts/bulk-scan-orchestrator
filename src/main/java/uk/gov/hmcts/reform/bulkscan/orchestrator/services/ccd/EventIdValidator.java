package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;
import javax.annotation.Nonnull;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;
import static java.util.Arrays.asList;

@Component
public final class EventIdValidator {

    private static final List<String> ATTACH_TO_CASE_EVENT_IDS = asList(
        EventIds.ATTACH_TO_CASE,
        EventIds.EXTEND_CAVEAT_CASE,
        EventIds.EXTEND_BULK_SCAN_CASE
    );

    @Nonnull
    public Validation<String, Void> isAttachToCaseEvent(String eventId) {
        return hasValidEventId(ATTACH_TO_CASE_EVENT_IDS::contains, eventId);
    }

    @Nonnull
    public Validation<String, Void> isCreateNewCaseEvent(String eventId) {
        return hasValidEventId(EventIds.CREATE_NEW_CASE::equals, eventId);
    }

    @Nonnull
    private Validation<String, Void> hasValidEventId(Function<String, Boolean> isValid, String eventId) {
        return isValid.apply(eventId)
            ? valid(null)
            : invalid(format("The %s event is not supported. Please contact service team", eventId));
    }
}
