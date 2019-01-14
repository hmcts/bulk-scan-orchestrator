package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisherContainer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.MessageOperations;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelopeJson;

@RunWith(MockitoJUnitRunner.class)
public class EnvelopeEventProcessorTest {

    @Mock
    private IMessage someMessage;

    @Mock
    private EventPublisherContainer eventPublisherContainer;

    @Mock
    private MessageOperations messageOperations;

    private EnvelopeEventProcessor processor;

    @Before
    public void before() {
        processor = new EnvelopeEventProcessor(mock(CaseRetriever.class), eventPublisherContainer, messageOperations);
        when(eventPublisherContainer.getPublisher(any(Classification.class), any()))
            .thenReturn(getDummyPublisher());

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
        verifyNoMoreInteractions(messageOperations);
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
            eq("Message processing error"),
            contains("JsonParseException")
        );

        verifyNoMoreInteractions(messageOperations);
    }

    @Test
    public void should_not_finalize_the_message_when_recoverable_failure() throws Exception {
        // given
        reset(eventPublisherContainer);
        willThrow(new RuntimeException("test exception"))
            .given(eventPublisherContainer)
            .getPublisher(any(), any());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);
        result.join();

        // then
        verifyNoMoreInteractions(messageOperations);
    }

    @Test
    public void should_not_do_anything_in_notify() {
        // when
        processor.notifyException(null, null);
    }

    private EventPublisher getDummyPublisher() {
        return new EventPublisher() {
            @Override
            public void publish(Envelope envelope) {
                //
            }

            @Override
            public void publish(Envelope envelope, String caseTypeId) {
                //
            }
        };
    }
}
