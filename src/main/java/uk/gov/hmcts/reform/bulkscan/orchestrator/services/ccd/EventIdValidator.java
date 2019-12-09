package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;

import java.util.function.Function;
import javax.annotation.Nonnull;

import static io.vavr.control.Validation.invalid;
import static io.vavr.control.Validation.valid;
import static java.lang.String.format;

public final class EventIdValidator {

    private static final String EVENT_ID_ATTACH_TO_CASE = "attachToExistingCase";
    public static final String EVENT_ID_CREATE_NEW_CASE = "createNewCase";
    public static final String EVENT_ID_ATTACH_SCANNED_DOCS_WITH_OCR = "attachScannedDocsWithOcr";

    private EventIdValidator() {
        // utility class constructor
    }

    @Nonnull
    static Validation<String, Void> isAttachToCaseEvent(String eventId) {
        return hasValidEventId(EVENT_ID_ATTACH_TO_CASE::equals, eventId);
    }

    @Nonnull
    static Validation<String, Void> isCreateNewCaseEvent(String eventId) {
        return hasValidEventId(EVENT_ID_CREATE_NEW_CASE::equals, eventId);
    }

    @Nonnull
    private static Validation<String, Void> hasValidEventId(Function<String, Boolean> isValid, String eventId) {
        return isValid.apply(eventId)
            ? valid(null)
            : invalid(format("The %s event is not supported. Please contact service team", eventId));
    }
}
