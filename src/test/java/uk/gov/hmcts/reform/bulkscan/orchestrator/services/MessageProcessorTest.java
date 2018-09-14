package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ReceiverProvider;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.MessageProcessor.TEST_MSG_LABEL;

@RunWith(MockitoJUnitRunner.class)
public class MessageProcessorTest {
    @Mock
    private ReceiverProvider receiverProvider;
    @Mock
    private IMessage someMessage;
    @Mock
    private IMessageReceiver receiver;
    @Mock
    private EnvelopeProcessor envelopeProcessor;

    private MessageProcessor messageProcessor;

    @Before
    public void setUp() {
        CompletableFuture<Void> value = new CompletableFuture<>();
        value.complete(null);
        when(this.envelopeProcessor.onMessageAsync(someMessage)).thenReturn(value);
        this.messageProcessor = new MessageProcessor(receiverProvider, envelopeProcessor);
        given(receiverProvider.get()).willReturn(receiver);
    }

    @Test
    public void should_handle_exceptions_off_receiver_get()
        throws ServiceBusException, InterruptedException {
        // given
        given(receiverProvider.get()).willThrow(new RuntimeException());

        // when
        messageProcessor.run();

        // then
        verify(envelopeProcessor, times(0)).onMessageAsync(someMessage);
        verify(receiver, times(0)).receive();
    }

    @Test
    public void should_read_all_messages_from_the_queue() throws Exception {
        // given
        // there are 2 messages on the queue
        given(receiver.receive())
            .willReturn(someMessage)
            .willReturn(someMessage)
            .willReturn(null);

        // when
        messageProcessor.run();

        // then
        verify(envelopeProcessor, times(2)).onMessageAsync(someMessage);
        verify(receiver, times(3)).receive();
    }


    @Test
    public void should_handle_interruptedException_completable_correctly() throws Exception {
        testCompletable(interruptedExceptionCompletable());
    }

    @Test
    public void should_handle_runtimeException_completable_correctly() throws Exception {
        testCompletable(runtimeExceptionCompletable());
    }


    @Test
    public void should_complete_message_after_it_is_successfully_processed() throws Exception {
        // given
        UUID lockToken = UUID.randomUUID();
        given(someMessage.getLockToken()).willReturn(lockToken);

        given(receiver.receive())
            .willReturn(someMessage)
            .willReturn(null);

        // when
        messageProcessor.run();

        // then
        verify(receiver).complete(eq(lockToken));
    }

    @Test
    public void should_not_read_envelopes_for_test_queue_messages() throws Exception {
        // given
        UUID lockToken = UUID.randomUUID();
        given(someMessage.getLockToken()).willReturn(lockToken);
        given(someMessage.getLabel()).willReturn(TEST_MSG_LABEL);

        given(receiver.receive())
            .willReturn(someMessage)
            .willReturn(null);

        // when
        messageProcessor.run();

        // then
        verify(receiver).complete(eq(lockToken));
        verify(envelopeProcessor, never()).onMessageAsync(any());
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<Void> interruptedExceptionCompletable()
        throws ExecutionException, InterruptedException {
        CompletableFuture<Void> mock = (CompletableFuture<Void>) mock(CompletableFuture.class);
        when(mock.get()).thenThrow(new InterruptedException());
        return mock;
    }

    private static CompletableFuture<Void> runtimeExceptionCompletable() {
        CompletableFuture<Void> value = new CompletableFuture<>();
        value.completeExceptionally(new RuntimeException());
        return value;
    }


    private void testCompletable(CompletableFuture<Void> completable) throws Exception {
        given(envelopeProcessor.onMessageAsync(someMessage)).willReturn(completable);
        given(receiver.receive())
            .willReturn(someMessage)
            .willReturn(someMessage)
            .willReturn(null);

        // when
        messageProcessor.run();

        // then
        verify(receiver, never()).complete(any());
    }


}
