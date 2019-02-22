package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.handler;

import org.junit.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

public class MessageProcessingResultTest {

    @Test
    public void should_return_second_object_when_first_was_successful() {
        // given
        MessageProcessingResult successResult = MessageProcessingResult.success();
        MessageProcessingResult recoverableResult = MessageProcessingResult.recoverable(new RuntimeException("oh no"));

        // when
        MessageProcessingResult flatResult = successResult.andThen(() -> recoverableResult);

        // then
        assertThat(flatResult).isEqualTo(recoverableResult);
    }

    @Test
    public void should_not_exec_further_steps_when_given_result_is_not_successful() {
        // given
        MessageProcessingResult result = MessageProcessingResult.unrecoverable(new RuntimeException("oh no"));
        Supplier<MessageProcessingResult> mockFunction = mock(TestSupplier.class);

        // when
        MessageProcessingResult flatResult = result.andThen(mockFunction);

        // then
        assertThat(result).isEqualTo(flatResult);
        verify(mockFunction, never()).get();
    }

    private static class TestSupplier implements Supplier<MessageProcessingResult> {

        @Override
        public MessageProcessingResult get() {
            return MessageProcessingResult.success();
        }
    }
}
