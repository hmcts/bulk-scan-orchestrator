package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class EventIdValidatorTest {

    private EventIdValidator eventIdValidator;

    @BeforeEach
    void setUp() {
        eventIdValidator = new EventIdValidator();
    }

    private static Object[][] attachToCaseEventIdTestParams() {
        return new Object[][]{
            {"Invalid 'Attach to Case' event id", "invalid_event_id", false},
            {"Invalid 'Attach to Case' event id", "AttachToExistingCase", false},
            {"Valid 'Attach to Case' event id", "attachToExistingCase", true},
            {"Valid 'Attach to Case' event id", "extendCaveatCase", true},
            {"Valid 'Attach to Case' event id", "extendBulkScanCase", true}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2}")
    @MethodSource("attachToCaseEventIdTestParams")
    void attachToCaseEventIdTest(String caseDescription, String eventId, boolean expectedIsValid) {
        Validation<String, Void> validation = eventIdValidator.isAttachToCaseEvent(eventId);

        assertThat(validation.isValid()).isEqualTo(expectedIsValid);
        assertErrorMessage(validation, eventId);
    }

    private static Object[][] createCaseEventIdTestParams() {
        return new Object[][]{
            {"Invalid 'Create Case' event id", "invalid_event_id", false},
            {"Valid 'Create Case' event id", EventIds.CREATE_NEW_CASE.toUpperCase(), false},
            {"Valid 'Create Case' event id", EventIds.CREATE_NEW_CASE, true}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2}")
    @MethodSource("createCaseEventIdTestParams")
    void createCaseEventIdTest(String caseDescription, String eventId, boolean expectedIsValid) {
        Validation<String, Void> validation = eventIdValidator.isCreateNewCaseEvent(eventId);

        assertThat(validation.isValid()).isEqualTo(expectedIsValid);
        assertErrorMessage(validation, eventId);
    }

    private void assertErrorMessage(Validation<String, Void> validation, String expectedEventId) {
        if (validation.isInvalid()) {
            assertThat(validation.getError()).isEqualTo(
                "The %s event is not supported. Please contact service team",
                expectedEventId
            );
        }
    }
}
