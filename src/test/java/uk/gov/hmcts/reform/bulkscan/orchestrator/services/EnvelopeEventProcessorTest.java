package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.NoUserConfiguredException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.UserService;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class EnvelopeEventProcessorTest {

    private EnvelopeEventProcessor processor;

    @Mock
    private IMessage someMessage;

    @Mock
    private UserService userService;

    @Before
    public void before() {
        processor = new EnvelopeEventProcessor(userService);
    }

    @Test
    public void should_return_completed_future_if_everything_went_fine() throws Exception {
        // given
        given(someMessage.getBody()).willReturn(SampleData.envelopeJson().getBytes());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isDone()).isTrue();
        assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    public void should_return_exceptionally_completed_future_if_queue_message_contains_invalid_envelope() {
        // given
        given(someMessage.getBody())
            .willReturn("foo".getBytes());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isCompletedExceptionally()).isTrue();
    }

    @Test
    public void should_not_do_anything_in_notify() {
        // when
        processor.notifyException(null, null);
    }

    @Test
    public void should_return_exceptionally_completed_future_if_unknown_jurisdiction() {
        // given
        given(userService.getBearerTokenForJurisdiction(any()))
            .willThrow(new NoUserConfiguredException("foo"));

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isCompletedExceptionally()).isTrue();
    }

}
