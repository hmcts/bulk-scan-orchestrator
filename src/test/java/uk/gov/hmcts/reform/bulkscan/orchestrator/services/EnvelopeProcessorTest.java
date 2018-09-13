package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.client.BulkScanProcessorClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.exceptions.ReadEnvelopeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ReceiverProvider;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class EnvelopeProcessorTest {

    @Mock private BulkScanProcessorClient bulkScanProcessorClient;
    @Mock private ReceiverProvider receiverProvider;

    @Mock private IMessage someMessage;
    @Mock private IMessageReceiver receiver;

    private EnvelopeProcessor processor;

    @Before
    public void setUp() throws Exception {
        this.processor = new EnvelopeProcessor(receiverProvider, bulkScanProcessorClient);
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
        processor.run();

        // then
        verify(bulkScanProcessorClient, times(2)).getEnvelopeById(any());
        verify(receiver, times(3)).receive();
    }

    @Test
    public void should_use_queue_message_id_to_read_envelope() throws Exception {
        // given
        final String msgId = "hello!";
        given(someMessage.getMessageId())
            .willReturn(msgId);

        given(receiver.receive())
            .willReturn(someMessage)
            .willReturn(null);

        // when
        processor.run();

        // then
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(bulkScanProcessorClient).getEnvelopeById(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue()).isEqualTo(msgId);
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
        processor.run();

        // then
        verify(receiver).complete(eq(lockToken));
    }

    @Test
    public void should_not_complete_message_if_envelope_cannot_be_read() throws Exception {
        // given
        given(bulkScanProcessorClient.getEnvelopeById(any()))
            .willThrow(ReadEnvelopeException.class);

        given(receiver.receive())
            .willReturn(someMessage)
            .willReturn(null);

        // when
        processor.run();

        // then
        verify(receiver, never()).complete(any());
    }

    @Test
    public void should_not_read_envelopes_for_test_queue_messages() throws Exception {
        // given
        UUID lockToken = UUID.randomUUID();
        given(someMessage.getLockToken()).willReturn(lockToken);
        given(someMessage.getLabel()).willReturn(EnvelopeProcessor.TEST_MSG_LABEL);

        given(receiver.receive())
            .willReturn(someMessage)
            .willReturn(null);

        // when
        processor.run();

        // then
        verify(receiver).complete(eq(lockToken));
        verify(bulkScanProcessorClient, never()).getEnvelopeById(any());
    }
}
