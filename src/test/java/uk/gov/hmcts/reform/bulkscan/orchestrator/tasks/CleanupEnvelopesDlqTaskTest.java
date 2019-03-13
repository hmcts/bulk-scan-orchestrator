package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(MockitoJUnitRunner.class)
public class CleanupEnvelopesDlqTaskTest {

    private CleanupEnvelopesDlqTask cleanupDlqTask;

    @Mock
    private IMessageReceiver messageReceiver;

    @Mock
    private IMessage message;

    @Mock
    private Supplier<IMessageReceiver> receiverProvider;

    @Captor
    ArgumentCaptor<UUID> uuidArgumentCaptor;

    @Before
    public void setUp() {
        cleanupDlqTask = new CleanupEnvelopesDlqTask(() -> messageReceiver, Duration.ofSeconds(10));
    }

    @Test
    public void should_delete_messages_from_dead_letter_queue() throws ServiceBusException, InterruptedException {
        //given
        UUID uuid = UUID.randomUUID();

        given(message.getLockToken()).willReturn(uuid);
        given(message.getBody()).willReturn(new JSONObject().toString().getBytes());
        given(message.getEnqueuedTimeUtc())
            .willReturn(LocalDateTime.now().minus(20, ChronoUnit.SECONDS).toInstant(ZoneOffset.UTC));
        given(messageReceiver.receive()).willReturn(message).willReturn(null);

        //when
        cleanupDlqTask.deleteMessagesInEnvelopesDlq();

        //then
        verify(messageReceiver).complete(uuidArgumentCaptor.capture());
        assertThat(uuidArgumentCaptor.getValue().equals(uuid));

        verify(messageReceiver, atLeastOnce()).complete(any());
        verify(messageReceiver, times(2)).receive();
        verify(messageReceiver, atLeastOnce()).close();
        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    public void should_not_delete_messages_from_dead_letter_queue_when_the_ttl_is_less_than_duration()
        throws ServiceBusException, InterruptedException {
        //given
        given(message.getEnqueuedTimeUtc())
            .willReturn(LocalDateTime.now().minus(5, ChronoUnit.SECONDS).toInstant(ZoneOffset.UTC));
        given(messageReceiver.receive()).willReturn(message).willReturn(null);

        //when
        cleanupDlqTask.deleteMessagesInEnvelopesDlq();

        //then
        verify(messageReceiver, times(2)).receive();
        verify(messageReceiver, never()).complete(any());
        verify(messageReceiver, atLeastOnce()).close();
        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    public void should_not_call_complete_when_no_messages_exists_in_dead_letter_queue()
        throws ServiceBusException, InterruptedException {
        //given
        given(messageReceiver.receive()).willReturn(null);

        //when
        cleanupDlqTask.deleteMessagesInEnvelopesDlq();

        //then
        verify(messageReceiver, atLeastOnce()).receive();
        verify(messageReceiver, never()).complete(any());
        verify(messageReceiver, atLeastOnce()).close();
        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    public void should_not_process_messages_when_connection_exception_is_thrown() {
        //given
        cleanupDlqTask = new CleanupEnvelopesDlqTask(receiverProvider, Duration.ZERO);

        doThrow(ConnectionException.class).when(receiverProvider).get();

        //when
        Throwable exception = catchThrowable(cleanupDlqTask::deleteMessagesInEnvelopesDlq);

        //then
        assertThat(exception).isNull();
    }

}
