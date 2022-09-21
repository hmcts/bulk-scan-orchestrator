package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusErrorSource;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.logging.AppInsights;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.envelopehandlers.EnvelopeHandler;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeMessageProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeProcessingResult;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.NotificationSendingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.ProcessedEnvelopeNotifier;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelopeJson;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Classification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@ExtendWith(MockitoExtension.class)
class EnvelopeMessageProcessorTest {

    private static final String DEAD_LETTER_REASON_PROCESSING_ERROR = "Message processing error";
    @Mock
    private ServiceBusReceivedMessageContext messageContext;

    @Mock
    private final ServiceBusReceivedMessage message = mock(ServiceBusReceivedMessage.class);

    @Mock
    private AppInsights appInsights;

    @Mock
    private EnvelopeHandler envelopeHandler;

    @Mock
    private ProcessedEnvelopeNotifier processedEnvelopeNotifier;

    @Mock
    private ServiceBusSenderClient envelopesDeadLetterSend;

    private static final Duration TTL = Duration.ofSeconds(10);

    private EnvelopeMessageProcessor processor;

    @BeforeEach
    void before() {
        processor = new EnvelopeMessageProcessor(
            envelopeHandler,
            processedEnvelopeNotifier,
            10,
            appInsights,
            envelopesDeadLetterSend,
            TTL
        );
    }

    @Test
    void should_not_throw_exception_when_queue_message_is_invalid() {

        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromString("invalid body"));

        assertThatCode(() -> processor.processMessage(messageContext)).doesNotThrowAnyException();

    }

    @Test
    void should_not_throw_exception_when_updating_ccd_fails() {
        // given
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromBytes(envelopeJson()));

        // and
        willThrow(new RuntimeException()).given(envelopeHandler).handleEnvelope(any(), anyLong());

        assertThatCode(() -> processor.processMessage(messageContext)).doesNotThrowAnyException();
    }

    @Test
    void should_complete_the_message_when_processing_is_successful() {
        // given
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromBytes(envelopeJson()));

        given(envelopeHandler.handleEnvelope(any(), anyLong()))
            .willReturn(new EnvelopeProcessingResult(3221L, EXCEPTION_RECORD));
        // when
        processor.processMessage(messageContext);

        // then
        verify(messageContext, times(3)).getMessage();
        verify(messageContext).complete();
        verifyNoMoreInteractions(appInsights, messageContext);
    }

    @Test
    @SuppressWarnings("checkstyle:variableDeclarationUsageDistance")
    void should_dead_letter_the_message_when_unrecoverable_failure() {
        // given
        var startTime = OffsetDateTime.now();
        String envelopeId = UUID.randomUUID().toString();
        given(message.getMessageId()).willReturn(envelopeId);

        given(messageContext.getMessage()).willReturn(message);
        var body = BinaryData.fromString("invalid body");

        given(message.getBody()).willReturn(body);

        // when
        processor.processMessage(messageContext);
        var endTime = OffsetDateTime.now();

        // then
        verify(messageContext, times(4)).getMessage();
        verify(messageContext).complete();

        ArgumentCaptor<ServiceBusMessage> messageArgumentCaptor
            = ArgumentCaptor.forClass(ServiceBusMessage.class);
        ArgumentCaptor<OffsetDateTime> scheduleMessageArgumentCaptor
            = ArgumentCaptor.forClass(OffsetDateTime.class);

        verify(envelopesDeadLetterSend).scheduleMessage(
            messageArgumentCaptor.capture(),
            scheduleMessageArgumentCaptor.capture()
        );

        var serviceBusMessage = messageArgumentCaptor.getValue();
        var scheduledTime = scheduleMessageArgumentCaptor.getValue();
        assertThat(serviceBusMessage.getMessageId())
            .isEqualTo(envelopeId);
        assertThat(serviceBusMessage.getBody().toString()).hasToString(body.toString());

        assertThat(scheduledTime)
            .isAfter(startTime.plus(TTL))
            .isBefore(endTime.plus(TTL));

        verify(appInsights).trackDeadLetteredMessage(
            eq(message),
            eq("envelopes"),
            eq(DEAD_LETTER_REASON_PROCESSING_ERROR),
            startsWith(JsonParseException.class.getCanonicalName())
        );
        verifyNoMoreInteractions(messageContext);
    }

    @Test
    void should_not_complete_the_message_when_notification_sending_fails() {
        // given
        String exceptionMessage = "test exception";
        willThrow(new NotificationSendingException(exceptionMessage, null))
            .given(processedEnvelopeNotifier)
            .notify(any(), any(), any());

        given(envelopeHandler.handleEnvelope(any(), anyLong()))
            .willReturn(new EnvelopeProcessingResult(3211321L, EXCEPTION_RECORD));

        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromBytes(envelopeJson()));

        // when
        processor.processMessage(messageContext);

        // then
        verify(messageContext, times(3)).getMessage();
        verifyNoMoreInteractions(messageContext);
    }

    @Test
    void should_not_finalize_the_message_when_recoverable_failure() {
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromBytes(envelopeJson()));

        Exception processingFailureCause = new RuntimeException(
            "exception of type treated as recoverable"
        );

        // given an error occurs during message processing
        willThrow(processingFailureCause).given(envelopeHandler).handleEnvelope(any(), anyLong());

        // when
        processor.processMessage(messageContext);

        // then the message is not finalised (completed/dead-lettered)
        verify(messageContext, times(3)).getMessage();
        verifyNoMoreInteractions(appInsights, messageContext);
    }

    @Test
    @SuppressWarnings("checkstyle:variableDeclarationUsageDistance")
    void should_finalize_the_message_when_recoverable_failure_but_delivery_maxed() {
        // given
        var startTime = OffsetDateTime.now();
        String envelopeId = UUID.randomUUID().toString();
        given(message.getMessageId()).willReturn(envelopeId);

        given(messageContext.getMessage()).willReturn(message);
        var body = BinaryData.fromBytes(envelopeJson());
        given(message.getBody()).willReturn(body);

        processor = new EnvelopeMessageProcessor(
            envelopeHandler,
            processedEnvelopeNotifier,
            1,
            appInsights,
            envelopesDeadLetterSend,
            TTL
        );
        Exception processingFailureCause = new RuntimeException(
            "exception of type treated as recoverable"
        );

        // and an error occurs during message processing
        willThrow(processingFailureCause).given(envelopeHandler).handleEnvelope(any(), anyLong());

        // when
        processor.processMessage(messageContext);
        var endTime = OffsetDateTime.now();

        // then the message is dead-lettered
        verify(messageContext).complete();
        ArgumentCaptor<ServiceBusMessage> messageArgumentCaptor
            = ArgumentCaptor.forClass(ServiceBusMessage.class);
        ArgumentCaptor<OffsetDateTime> scheduleMessageArgumentCaptor
            = ArgumentCaptor.forClass(OffsetDateTime.class);

        verify(envelopesDeadLetterSend).scheduleMessage(
            messageArgumentCaptor.capture(),
            scheduleMessageArgumentCaptor.capture()
        );

        var serviceBusMessage = messageArgumentCaptor.getValue();
        var scheduledTime = scheduleMessageArgumentCaptor.getValue();
        assertThat(serviceBusMessage.getMessageId()).hasToString(envelopeId);
        assertThat(serviceBusMessage.getBody().toString()).hasToString(body.toString());

        assertThat(scheduledTime)
            .isAfter(startTime.plus(TTL))
            .isBefore(endTime.plus(TTL));


        verify(appInsights).trackDeadLetteredMessage(
            message,
            "envelopes",
            "Too many deliveries",
            "Reached limit of message delivery count of 1"
        );
    }

    @Test
    void should_send_message_with_envelope_id_when_processing_successful() {
        // given
        String envelopeId = UUID.randomUUID().toString();
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody())
            .willReturn(BinaryData.fromBytes(envelopeJson(NEW_APPLICATION, "caseRef123", envelopeId)));


        given(messageContext.getMessage()).willReturn(message);
        given(envelopeHandler.handleEnvelope(any(), anyLong()))
            .willReturn(new EnvelopeProcessingResult(3211321L, EXCEPTION_RECORD));

        // when
        processor.processMessage(messageContext);

        // then
        verify(processedEnvelopeNotifier).notify(envelopeId, 3211321L, EXCEPTION_RECORD);
    }

    @Test
    void should_not_send_processed_envelope_notification_when_processing_fails() {
        // given
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromBytes(envelopeJson()));

        // and
        Exception processingFailureCause = new RuntimeException("test exception");
        given(envelopeHandler.handleEnvelope(any(), anyLong())).willThrow(processingFailureCause);

        // when
        processor.processMessage(messageContext);

        // then no notification is sent
        verifyNoMoreInteractions(processedEnvelopeNotifier);
    }

    @Test
    void should_throw_exception_when_message_receiver_fails() {
        ServiceBusException receiverException =
            new ServiceBusException(new IllegalAccessException("test exception"), ServiceBusErrorSource.MANAGEMENT);
        given(messageContext.getMessage()).willThrow(receiverException);

        assertThatThrownBy(() -> processor.processMessage(messageContext))
            .isSameAs(receiverException);
    }

    @Test
    void should_not_treat_heartbeat_messages_as_envelopes() {
        // given
        given(messageContext.getMessage()).willReturn(message);
        given(message.getSubject()).willReturn(EnvelopeMessageProcessor.HEARTBEAT_LABEL);

        // when
        processor.processMessage(messageContext);

        // then
        verifyNoInteractions(
            envelopeHandler,
            processedEnvelopeNotifier
        );
    }

    @Test
    void should_handle_null_message() {
        // given
        given(messageContext.getMessage()).willReturn(null);

        // when
        processor.processMessage(messageContext);

        // then
        verifyNoInteractions(
            envelopeHandler,
            processedEnvelopeNotifier
        );
    }

    @Test
    void should_handle_runtime_exception_when_finalizing_processed_message() {
        // given
        given(messageContext.getMessage()).willReturn(message);
        given(message.getBody()).willReturn(BinaryData.fromBytes(envelopeJson()));
        willThrow(new RuntimeException()).given(messageContext).complete();

        given(envelopeHandler.handleEnvelope(any(), anyLong()))
                .willReturn(new EnvelopeProcessingResult(3221L, EXCEPTION_RECORD));

        // when
        assertThatCode(() -> processor.processMessage(messageContext)).doesNotThrowAnyException();

        // then
        verify(messageContext, times(3)).getMessage();
        verify(messageContext).complete();
        verifyNoMoreInteractions(appInsights, messageContext);
    }
}
