package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;
import static java.util.Arrays.asList;

@Component
public class EventIdValidator {

    private static final List<String> ATTACH_TO_CASE_EVENT_IDS = asList(
        EventIds.ATTACH_TO_CASE,
        EventIds.EXTEND_CAVEAT_CASE,
        EventIds.EXTEND_BULK_SCAN_CASE
    );

    @Nonnull
    Validation<String, Void> isAttachToCaseEvent(String eventId) {
        return hasValidEventId(ATTACH_TO_CASE_EVENT_IDS::contains, eventId);
    }

    @Nonnull
    public Validation<String, Void> isCreateNewCaseEvent(String eventId) {
        return hasValidEventId(EventIds.CREATE_NEW_CASE::equals, eventId);
    }

    @Nonnull
    private Validation<String, Void> hasValidEventId(Predicate<String> isValid, String eventId) {
        return isValid.test(eventId)
            ? valid(null)
            : invalid(format("The %s event is not supported. Please contact service team", eventId));
    }
}
