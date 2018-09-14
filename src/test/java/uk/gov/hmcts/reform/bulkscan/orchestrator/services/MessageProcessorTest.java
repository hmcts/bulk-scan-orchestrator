package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ReceiverProvider;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
    public void should_not_complete_message_if_envelope_cannot_be_read() throws Exception {
        // given
        CompletableFuture<Void> value = new CompletableFuture<>();
        value.completeExceptionally(new RuntimeException());
        given(envelopeProcessor.onMessageAsync(someMessage)).willReturn(value);
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


}
