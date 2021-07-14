package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.azure.core.util.BinaryData;
import com.azure.core.util.IterableStream;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
class CleanupEnvelopesDlqTaskTest {

    private CleanupEnvelopesDlqTask cleanupDlqTask;

    @Mock
    private ServiceBusReceiverClient messageReceiver;

    @Mock
    private ServiceBusReceivedMessage message;

    @Mock
    private IterableStream<ServiceBusReceivedMessage> iterableStream;

    @Mock
    private Supplier<ServiceBusReceiverClient> receiverProvider;

    private final Duration ttl = Duration.ofSeconds(10);

    @BeforeAll
    static void init() {
        TimeZone.setDefault(TimeZone.getTimeZone(UTC));
    }

    @BeforeEach
    void setUp() {
        cleanupDlqTask = new CleanupEnvelopesDlqTask(() -> messageReceiver, ttl);
    }

    @Test
    void should_delete_messages_from_dlq() throws Exception {
        //given
        UUID uuid = UUID.randomUUID();
        given(message.getBody())
            .willReturn(BinaryData.fromBytes(SampleData.envelopeJson()));
        given(message.getApplicationProperties())
            .willReturn(
                ImmutableMap.of(
                    "deadLetteredAt",
                    LocalDateTime.now().minus(ttl.plusSeconds(10)).toInstant(UTC).toString()
                )
            );


        given(messageReceiver.receiveMessages(1, Duration.ofSeconds(1)))
            .willReturn(iterableStream);

        given(iterableStream.stream()).willReturn(Stream.of(message)).willReturn(Stream.empty());

        //when
        cleanupDlqTask.deleteMessagesInEnvelopesDlq();

        //then
        verify(messageReceiver).renewMessageLock(message);
        verify(messageReceiver).complete(message);

        verify(messageReceiver, times(1)).complete(any());
        verify(messageReceiver, times(2)).receiveMessages(1, Duration.ofSeconds(1));
        verify(messageReceiver, times(1)).close();
        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    void should_not_delete_messages_from_dlq_when_deadLetteredTime_is_not_set()
        throws Exception {
        //given
        given(messageReceiver.receiveMessages(1, Duration.ofSeconds(1)))
            .willReturn(iterableStream);
        given(iterableStream.stream()).willReturn(Stream.of(message)).willReturn(Stream.empty());

        //when
        cleanupDlqTask.deleteMessagesInEnvelopesDlq();

        //then
        verify(messageReceiver).renewMessageLock(message);
        verify(messageReceiver, times(2)).receiveMessages(1, Duration.ofSeconds(1));
        verify(messageReceiver, never()).complete(any());
        verify(messageReceiver, times(1)).close();
        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    void should_leave_message_on_dlq_when_the_ttl_is_less_than_duration() throws Exception {
        //given
        given(message.getApplicationProperties())
            .willReturn(
                ImmutableMap.of(
                    "deadLetteredAt",
                    LocalDateTime.now().minus(ttl.minusSeconds(5)).toInstant(UTC).toString()
                )
            );
        given(messageReceiver.receiveMessages(1, Duration.ofSeconds(1)))
            .willReturn(iterableStream);

        given(iterableStream.stream()).willReturn(Stream.of(message)).willReturn(Stream.empty());

        //when
        cleanupDlqTask.deleteMessagesInEnvelopesDlq();

        //then
        verify(messageReceiver).renewMessageLock(message);
        verify(messageReceiver, times(2)).receiveMessages(1, Duration.ofSeconds(1));
        verify(messageReceiver, never()).complete(any());
        verify(messageReceiver, times(1)).close();
        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    void should_not_complete_when_no_message_exists_in_dlq() throws Exception {
        //given
        given(messageReceiver.receiveMessages(1, Duration.ofSeconds(1)))
            .willReturn(iterableStream);

        given(iterableStream.stream()).willReturn(Stream.empty());

        //when
        cleanupDlqTask.deleteMessagesInEnvelopesDlq();

        //then
        verify(messageReceiver,never()).renewMessageLock(any());
        verify(messageReceiver, times(1)).receiveMessages(1, Duration.ofSeconds(1));
        verify(messageReceiver, never()).complete(any());
        verify(messageReceiver, never()).abandon(any());
        verify(messageReceiver, times(1)).close();
        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    void should_not_process_messages_when_exception_is_thrown() {
        //given
        cleanupDlqTask = new CleanupEnvelopesDlqTask(receiverProvider, Duration.ZERO);

        doThrow(ConnectionException.class).when(receiverProvider).get();

        //when
        Throwable exception = catchThrowable(cleanupDlqTask::deleteMessagesInEnvelopesDlq);

        //then
        assertThat(exception).isNull();
    }

}
