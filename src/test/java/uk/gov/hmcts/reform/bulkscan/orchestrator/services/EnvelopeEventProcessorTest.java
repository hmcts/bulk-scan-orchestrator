package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.fasterxml.jackson.core.JsonParseException;
import com.microsoft.azure.servicebus.IMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.logging.AppInsights;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisherContainer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.MessageOperations;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.NotificationSendingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;

import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelopeJson;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification.NEW_APPLICATION;

@RunWith(MockitoJUnitRunner.class)
public class EnvelopeEventProcessorTest {

    private static final String DEAD_LETTER_REASON_PROCESSING_ERROR = "Message processing error";

    @Mock
    private IMessage someMessage;

    @Mock
    private EventPublisherContainer eventPublisherContainer;

    @Mock
    private MessageOperations messageOperations;

    @Mock
    private AppInsights appInsights;

    @Mock
    EventPublisher eventPublisher;

    @Mock
    private ProcessedEnvelopeNotifier processedEnvelopeNotifier;

    private EnvelopeEventProcessor processor;

    @Before
    public void before() {
        processor = new EnvelopeEventProcessor(
            mock(CaseRetriever.class),
            eventPublisherContainer,
            processedEnvelopeNotifier,
            messageOperations,
            10,
            appInsights
        );

        when(eventPublisherContainer.getPublisher(any(Classification.class), any()))
            .thenReturn(eventPublisher);

        given(someMessage.getBody()).willReturn(envelopeJson());
        given(someMessage.getLockToken()).willReturn(UUID.randomUUID());
    }

    @Test
    public void should_return_completed_future_if_everything_went_fine() {
        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isDone()).isTrue();
        assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    public void should_return_completed_future_if_queue_message_contains_invalid_envelope() {
        // given
        given(someMessage.getBody()).willReturn("foo".getBytes());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    public void should_return_completed_future_if_exception_is_thrown_while_retrieving_case() {
        // given
        reset(eventPublisherContainer);
        given(eventPublisherContainer.getPublisher(any(), any())).willThrow(new RuntimeException());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    public void should_complete_the_message_when_processing_is_successful() throws Exception {
        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);
        result.join();

        // then
        verify(messageOperations).complete(someMessage.getLockToken());
        verifyNoMoreInteractions(appInsights, messageOperations);
    }

    @Test
    public void should_dead_letter_the_message_when_unrecoverable_failure() throws Exception {
        // given
        IMessage message = mock(IMessage.class);
        given(message.getBody()).willReturn("invalid body".getBytes(Charset.defaultCharset()));
        given(message.getLockToken()).willReturn(UUID.randomUUID());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(message);
        result.join();

        // then
        verify(messageOperations).deadLetter(
            eq(message.getLockToken()),
            eq(DEAD_LETTER_REASON_PROCESSING_ERROR),
            contains(JsonParseException.class.getSimpleName())
        );
        verify(appInsights).trackDeadLetteredMessage(
            eq(message),
            eq("envelopes"),
            eq(DEAD_LETTER_REASON_PROCESSING_ERROR),
            startsWith(JsonParseException.class.getCanonicalName())
        );

        verifyNoMoreInteractions(messageOperations);
    }

    @Test
    public void should_dead_letter_the_message_when_notification_sending_fails() throws Exception {
        // given
        String exceptionMessage = "test exception";

        willThrow(new NotificationSendingException(exceptionMessage, null))
            .given(processedEnvelopeNotifier)
            .notify(any());


        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);
        result.join();

        // then
        verify(messageOperations).deadLetter(
            eq(someMessage.getLockToken()),
            eq(DEAD_LETTER_REASON_PROCESSING_ERROR),
            eq(exceptionMessage)
        );
        verify(appInsights).trackDeadLetteredMessage(
            someMessage,
            "envelopes",
            DEAD_LETTER_REASON_PROCESSING_ERROR,
            exceptionMessage
        );

        verifyNoMoreInteractions(messageOperations);
    }

    @Test
    public void should_not_finalize_the_message_when_recoverable_failure() {
        Exception processingFailureCause = new RuntimeException(
            "exception of type treated as recoverable"
        );

        // given an error occurs during message processing
        willThrow(processingFailureCause).given(eventPublisher).publish(any());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);
        result.join();

        // then the message is not finalised (completed/dead-lettered)
        verifyNoMoreInteractions(appInsights, messageOperations);
    }

    @Test
    public void should_finalize_the_message_when_recoverable_failure_but_delivery_maxed() throws Exception {
        // given
        processor = new EnvelopeEventProcessor(
            mock(CaseRetriever.class),
            eventPublisherContainer,
            processedEnvelopeNotifier,
            messageOperations,
            1,
            appInsights
        );
        Exception processingFailureCause = new RuntimeException(
            "exception of type treated as recoverable"
        );

        // and an error occurs during message processing
        willThrow(processingFailureCause).given(eventPublisher).publish(any());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);
        result.join();

        // then the message is dead-lettered
        verify(messageOperations).deadLetter(
            someMessage.getLockToken(),
            "Too many deliveries",
            "Breached the limit of message delivery count of 1"
        );
        verify(appInsights).trackDeadLetteredMessage(
            someMessage,
            "envelopes",
            "Too many deliveries",
            "Breached the limit of message delivery count of 1"
        );
    }

    @Test
    public void notify_exception_should_not_throw_exception() {
        assertThatCode(() ->
            processor.notifyException(null, null)
        ).doesNotThrowAnyException();
    }

    @Test
    public void should_send_message_with_envelope_id_when_processing_successful() {
        // given
        String envelopeId = UUID.randomUUID().toString();
        IMessage message = mock(IMessage.class);
        given(message.getBody()).willReturn(envelopeJson(NEW_APPLICATION, "caseRef123", envelopeId));
        given(message.getLockToken()).willReturn(UUID.randomUUID());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(message);
        result.join();

        // then
        verify(processedEnvelopeNotifier).notify(envelopeId);
    }

    @Test
    public void should_not_send_processed_envelope_notification_when_processing_fails() {
        // given
        Exception processingFailureCause = new RuntimeException("test exception");
        willThrow(processingFailureCause).given(eventPublisher).publish(any());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);
        result.join();

        // then no notification is sent
        verifyNoMoreInteractions(processedEnvelopeNotifier);
    }
}
