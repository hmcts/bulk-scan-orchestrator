package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Supplier;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class CleanupEnvelopesDlqTaskTest {

    private CleanupEnvelopesDlqTask cleanupDlqTask;

    @Mock
    private IMessageReceiver messageReceiver;

    @Mock
    private IMessage message;

    @Mock
    private Supplier<IMessageReceiver> receiverProvider;

    private final Duration ttl = Duration.ofSeconds(10);

    @BeforeAll
    public static void init() {
        TimeZone.setDefault(TimeZone.getTimeZone(UTC));
    }

    @BeforeEach
    public void setUp() {
        cleanupDlqTask = new CleanupEnvelopesDlqTask(() -> messageReceiver, ttl);
    }

    @Test
    public void should_delete_messages_from_dead_letter_queue() throws Exception {
        //given
        UUID uuid = UUID.randomUUID();
        given(message.getLockToken()).willReturn(uuid);
        given(message.getBody()).willReturn(SampleData.envelopeJson());
        given(message.getEnqueuedTimeUtc())
            .willReturn(LocalDateTime.now().minus(ttl.plusSeconds(10)).toInstant(ZoneOffset.UTC));
        given(messageReceiver.receive()).willReturn(message).willReturn(null);

        ArgumentCaptor<UUID> uuidArgumentCaptor = ArgumentCaptor.forClass(UUID.class);

        //when
        cleanupDlqTask.deleteMessagesInEnvelopesDlq();

        //then
        verify(messageReceiver).complete(uuidArgumentCaptor.capture());
        assertThat(uuidArgumentCaptor.getValue()).isEqualTo(uuid);

        verify(messageReceiver, times(1)).complete(any());
        verify(messageReceiver, times(2)).receive();
        verify(messageReceiver, times(1)).close();
        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    public void should_call_abandon_message_when_the_ttl_is_less_than_duration()
        throws Exception {
        //given
        given(message.getEnqueuedTimeUtc())
            .willReturn(LocalDateTime.now().minus(ttl.minusSeconds(5)).toInstant(ZoneOffset.UTC));
        given(messageReceiver.receive()).willReturn(message).willReturn(null);

        //when
        cleanupDlqTask.deleteMessagesInEnvelopesDlq();

        //then
        verify(messageReceiver, times(2)).receive();
        verify(messageReceiver, never()).complete(any());
        verify(messageReceiver, times(1)).abandon(any());
        verify(messageReceiver, times(1)).close();
        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    public void should_not_call_complete_when_no_messages_exists_in_dead_letter_queue() throws Exception {
        //given
        given(messageReceiver.receive()).willReturn(null);

        //when
        cleanupDlqTask.deleteMessagesInEnvelopesDlq();

        //then
        verify(messageReceiver, times(1)).receive();
        verify(messageReceiver, never()).complete(any());
        verify(messageReceiver, never()).abandon(any());
        verify(messageReceiver, times(1)).close();
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
