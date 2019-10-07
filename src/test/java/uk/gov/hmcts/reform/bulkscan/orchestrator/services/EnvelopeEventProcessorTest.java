package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.common.collect.ImmutableList;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.MessageBody;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.logging.AppInsights;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EnvelopeHandler;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.NotificationSendingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeEventProcessor;

import java.nio.charset.Charset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelopeJson;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;

@ExtendWith(MockitoExtension.class)
class EnvelopeEventProcessorTest {

    private static final String DEAD_LETTER_REASON_PROCESSING_ERROR = "Message processing error";

    @Mock
    private IMessageReceiver messageReceiver;

    @Mock
    private AppInsights appInsights;

    @Mock
    EnvelopeHandler envelopeHandler;

    @Mock
    private ProcessedEnvelopeNotifier processedEnvelopeNotifier;

    private EnvelopeEventProcessor processor;

    @BeforeEach
    void before() throws Exception {
        processor = new EnvelopeEventProcessor(
            envelopeHandler,
            processedEnvelopeNotifier,
            messageReceiver,
            10,
            appInsights
        );
    }

    @Test
    public void should_return_true_when_there_is_a_message_to_process() throws Exception {
        // given
        willReturn(getValidMessage()).given(messageReceiver).receive();

        // when
        boolean processedMessage = processor.processNextMessage();

        // then
        assertThat(processedMessage).isTrue();
    }

    @Test
    public void should_return_false_when_there_is_no_message_to_process() throws Exception {
        // given
        given(messageReceiver.receive()).willReturn(null);

        // when
        boolean processedMessage = processor.processNextMessage();

        // then
        assertThat(processedMessage).isFalse();
    }

    @Test
    public void should_not_throw_exception_when_queue_message_is_invalid() throws Exception {
        IMessage invalidMessage = mock(IMessage.class);
        given(invalidMessage.getMessageBody())
            .willReturn(MessageBody.fromBinaryData(ImmutableList.of("foo".getBytes())));
        given(messageReceiver.receive()).willReturn(invalidMessage);

        assertThat(processor.processNextMessage()).isTrue();
    }

    @Test
    public void should_not_throw_exception_when_updating_ccd_fails() throws Exception {
        // given
        willReturn(getValidMessage()).given(messageReceiver).receive();

        // and
        willThrow(new RuntimeException()).given(envelopeHandler).handleEnvelope(any());

        assertThatCode(() -> processor.processNextMessage()).doesNotThrowAnyException();
    }

    @Test
    public void should_complete_the_message_when_processing_is_successful() throws Exception {
        // given
        IMessage validMessage = getValidMessage();
        given(messageReceiver.receive()).willReturn(validMessage);

        // when
        processor.processNextMessage();

        // then
        verify(messageReceiver).receive();
        verify(messageReceiver).complete(validMessage.getLockToken());
        verifyNoMoreInteractions(appInsights, messageReceiver);
    }

    @Test
    public void should_dead_letter_the_message_when_unrecoverable_failure() throws Exception {
        // given
        IMessage message = mock(IMessage.class);
        given(message.getMessageBody()).willReturn(
            MessageBody.fromBinaryData(ImmutableList.of("invalid body".getBytes(Charset.defaultCharset())))
        );
        given(message.getLockToken()).willReturn(UUID.randomUUID());
        given(messageReceiver.receive()).willReturn(message);

        // when
        processor.processNextMessage();

        // then
        verify(messageReceiver).receive();

        verify(messageReceiver).deadLetter(
            eq(message.getLockToken()),
            eq(DEAD_LETTER_REASON_PROCESSING_ERROR),
            contains(JsonParseException.class.getSimpleName()),
            any()
        );
        verify(appInsights).trackDeadLetteredMessage(
            eq(message),
            eq("envelopes"),
            eq(DEAD_LETTER_REASON_PROCESSING_ERROR),
            startsWith(JsonParseException.class.getCanonicalName())
        );

        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    public void should_not_complete_the_message_when_notification_sending_fails() throws Exception {
        // given
        String exceptionMessage = "test exception";
        willThrow(new NotificationSendingException(exceptionMessage, null))
            .given(processedEnvelopeNotifier)
            .notify(any());

        IMessage validMessage = getValidMessage();
        given(messageReceiver.receive()).willReturn(validMessage);


        // when
        processor.processNextMessage();

        // then
        verify(messageReceiver).receive();
        verifyNoMoreInteractions(messageReceiver);
    }

    @Test
    public void should_not_finalize_the_message_when_recoverable_failure() throws Exception {
        willReturn(getValidMessage()).given(messageReceiver).receive();

        Exception processingFailureCause = new RuntimeException(
            "exception of type treated as recoverable"
        );

        // given an error occurs during message processing
        willThrow(processingFailureCause).given(envelopeHandler).handleEnvelope(any());

        // when
        processor.processNextMessage();

        // then the message is not finalised (completed/dead-lettered)
        verify(messageReceiver).receive();
        verifyNoMoreInteractions(appInsights, messageReceiver);
    }

    @Test
    public void should_finalize_the_message_when_recoverable_failure_but_delivery_maxed() throws Exception {
        // given
        IMessage validMessage = getValidMessage();
        given(messageReceiver.receive()).willReturn(validMessage);

        processor = new EnvelopeEventProcessor(
            envelopeHandler,
            processedEnvelopeNotifier,
            messageReceiver,
            1,
            appInsights
        );
        Exception processingFailureCause = new RuntimeException(
            "exception of type treated as recoverable"
        );

        // and an error occurs during message processing
        willThrow(processingFailureCause).given(envelopeHandler).handleEnvelope(any());

        // when
        processor.processNextMessage();

        // then the message is dead-lettered
        verify(messageReceiver).deadLetter(
            eq(validMessage.getLockToken()),
            eq("Too many deliveries"),
            eq("Reached limit of message delivery count of 1"),
            any()
        );
        verify(appInsights).trackDeadLetteredMessage(
            validMessage,
            "envelopes",
            "Too many deliveries",
            "Reached limit of message delivery count of 1"
        );
    }

    @Test
    public void should_send_message_with_envelope_id_when_processing_successful() throws Exception {
        // given
        String envelopeId = UUID.randomUUID().toString();
        IMessage message = mock(IMessage.class);
        given(message.getMessageBody()).willReturn(
            MessageBody.fromBinaryData(ImmutableList.of(envelopeJson(NEW_APPLICATION, "caseRef123", envelopeId)))
        );
        given(message.getLockToken()).willReturn(UUID.randomUUID());
        given(messageReceiver.receive()).willReturn(message);

        // when
        processor.processNextMessage();

        // then
        verify(processedEnvelopeNotifier).notify(envelopeId);
    }

    @Test
    public void should_not_send_processed_envelope_notification_when_processing_fails() throws Exception {
        // given
        willReturn(getValidMessage()).given(messageReceiver).receive();

        // and
        Exception processingFailureCause = new RuntimeException("test exception");
        willThrow(processingFailureCause).given(envelopeHandler).handleEnvelope(any());

        // when
        processor.processNextMessage();

        // then no notification is sent
        verifyNoMoreInteractions(processedEnvelopeNotifier);
    }

    @Test
    public void should_throw_exception_when_message_receiver_fails() throws Exception {
        ServiceBusException receiverException = new ServiceBusException(true);
        willThrow(receiverException).given(messageReceiver).receive();

        assertThatThrownBy(() -> processor.processNextMessage())
            .isSameAs(receiverException);
    }

    @Test
    public void should_not_treat_heartbeat_messages_as_envelopes() throws Exception {
        // given
        IMessage message = mock(IMessage.class);
        given(message.getLabel()).willReturn(EnvelopeEventProcessor.HEARTBEAT_LABEL);

        given(messageReceiver.receive()).willReturn(message);

        // when
        processor.processNextMessage();

        // then
        verifyNoInteractions(
            envelopeHandler,
            processedEnvelopeNotifier
        );
    }

    private IMessage getValidMessage() {
        IMessage message = mock(IMessage.class);
        given(message.getMessageBody())
            .willReturn(MessageBody.fromBinaryData(ImmutableList.of(envelopeJson())));
        return message;
    }
}
