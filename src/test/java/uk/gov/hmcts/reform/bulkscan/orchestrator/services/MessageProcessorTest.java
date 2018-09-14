package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
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
import static org.mockito.quality.Strictness.LENIENT;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.MessageProcessor.TEST_MSG_LABEL;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class MessageProcessorTest {
    @Mock
    private ReceiverProvider receiverProvider;
    @Mock
    private IMessage someMessage;
    @Mock
    private IMessageReceiver receiver;
    @Mock
    private EnvelopeProcessor envelopeProcessor;

    private MessageProcessor messageProcessor;

    @BeforeEach
    void setUp() {
        CompletableFuture<Void> value = new CompletableFuture<>();
        value.complete(null);
        when(this.envelopeProcessor.onMessageAsync(someMessage)).thenReturn(value);
        this.messageProcessor = new MessageProcessor(receiverProvider, envelopeProcessor);
        given(receiverProvider.get()).willReturn(receiver);
    }

    @Test
    @DisplayName("If the receiver get throws and exception it is handled correctly")
    void receiverGetTest() throws ServiceBusException, InterruptedException {
        // given
        given(receiverProvider.get()).willThrow(new RuntimeException());

        // when
        messageProcessor.run();

        // then
        verify(envelopeProcessor, times(0)).onMessageAsync(someMessage);
        verify(receiver, times(0)).receive();
    }

    @Test
    void should_read_all_messages_from_the_queue() throws Exception {
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

    private static Object[][] testExceptionsParam() throws ExecutionException, InterruptedException {
        return new Object[][]{
            {runtimeExceptionCompletable(), "RuntimeException"},
            {interruptedExceptionCompletable(), "ServiceBusException"}
        };
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<Void> interruptedExceptionCompletable() throws ExecutionException, InterruptedException {
        CompletableFuture<Void> mock = (CompletableFuture<Void>) mock(CompletableFuture.class);
        when(mock.get()).thenThrow(new InterruptedException());
        return mock;
    }

    private static CompletableFuture<Void> runtimeExceptionCompletable() {
        CompletableFuture<Void> value = new CompletableFuture<>();
        value.completeExceptionally(new RuntimeException());
        return value;
    }

    @ParameterizedTest(name = "{index} {1}")
    @MethodSource("testExceptionsParam")
    @DisplayName("Test all the exception variants from the receiver/processor")
    void testExceptions(CompletableFuture<Void> completable, String name) throws Exception {
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


    @Test
    void should_complete_message_after_it_is_successfully_processed() throws Exception {
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
    void should_not_read_envelopes_for_test_queue_messages() throws Exception {
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


}
