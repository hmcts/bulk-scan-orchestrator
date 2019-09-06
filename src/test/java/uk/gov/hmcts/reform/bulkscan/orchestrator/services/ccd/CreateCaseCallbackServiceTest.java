package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import io.vavr.control.Either;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCaseCallbackServiceTest {

    private static final String EVENT_ID = "createCase";
    private static final CreateCaseCallbackService SERVICE = new CreateCaseCallbackService();

    @Test
    void should_not_allow_to_process_callback_in_case_wrong_event_id_is_received() {
        Either<List<String>, Object> output = SERVICE.process(null, "some event");

        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("The some event event is not supported. Please contact service team");
    }

    @Test
    void should_proceed_with_not_implemented_error() {
        Either<List<String>, Object> output = SERVICE.process(null, EVENT_ID);

        assertThat(output.isLeft()).isTrue();
        assertThat(output.getLeft()).containsOnly("Not yet implemented");
    }
}
