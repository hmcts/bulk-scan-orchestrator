package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class EventIdValidatorTest {

    private static Object[][] attachToCaseEventIdTestParams() {
        return new Object[][]{
            {"Invalid 'Attach to Case' event id", "invalid_event_id", false},
            {"Valid 'Attach to Case' event id", "attachToExistingCase", true}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2}")
    @MethodSource("attachToCaseEventIdTestParams")
    void attachToCaseEventIdTest(String caseDescription, String eventId, boolean expectedIsValid) {
        assertEventIdValidation(EventIdValidator.isAttachToCaseEvent(eventId), expectedIsValid);
    }

    private static Object[][] createCaseEventIdTestParams() {
        return new Object[][]{
            {"Invalid 'Create Case' event id", "invalid_event_id", false},
            {"Valid 'Create Case' event id", "createCase", true}
        };
    }

    @ParameterizedTest(name = "{0}: valid:{2}")
    @MethodSource("createCaseEventIdTestParams")
    void createCaseEventIdTest(String caseDescription, String eventId, boolean expectedIsValid) {
        assertEventIdValidation(EventIdValidator.isCreateCaseEvent(eventId), expectedIsValid);
    }

    private void assertEventIdValidation(Validation<String, Void> validation, boolean expectedIsValid) {
        assertThat(validation.isValid()).isEqualTo(expectedIsValid);

        if (validation.isInvalid()) {
            assertThat(validation.getError()).isEqualTo(
                "The invalid_event_id event is not supported. Please contact service team"
            );
        }
    }
}
