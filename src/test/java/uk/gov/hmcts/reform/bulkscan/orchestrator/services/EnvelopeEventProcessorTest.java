package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisherContainer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.CASE_REF;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.JURSIDICTION;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.THE_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelopeJson;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher.CASE_TYPE_BULK_SCANNED;

@RunWith(MockitoJUnitRunner.class)
public class EnvelopeEventProcessorTest {
    @Mock
    private IMessage someMessage;
    @Mock
    private CaseRetriever caseRetriever;
    @Mock
    private EventPublisherContainer eventPublisherContainer;
    @Mock
    private CcdAuthenticator authInfo;

    private EnvelopeEventProcessor processor;

    @Before
    public void before() {
        processor = new EnvelopeEventProcessor(caseRetriever, eventPublisherContainer);
        when(caseRetriever.retrieve(eq(JURSIDICTION), eq(CASE_TYPE_BULK_SCANNED), eq(CASE_REF))).thenReturn(THE_CASE);
        when(eventPublisherContainer.getPublisher(any(Envelope.class), any(CaseDetails.class)))
            .thenReturn(getDummyPublisher());
        when(eventPublisherContainer.getPublisher(any(Envelope.class), eq(null)))
            .thenReturn(getDummyPublisher());
        given(someMessage.getBody()).willReturn(envelopeJson());
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
    public void should_return_completed_future_when_case_is_null() {
        reset(someMessage);
        given(someMessage.getBody()).willReturn(envelopeJson(""));

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isDone()).isTrue();
        assertThat(result.isCompletedExceptionally()).isFalse();
    }

    @Test
    public void should_return_exceptionally_completed_future_if_queue_message_contains_invalid_envelope() {
        // given
        given(someMessage.getBody())
            .willReturn("foo".getBytes());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isCompletedExceptionally()).isTrue();
    }

    @Test
    public void should_not_do_anything_in_notify() {
        // when
        processor.notifyException(null, null);
    }

    @Test
    public void should_return_exceptionally_completed_future_if_exception_is_thrown() throws Exception {
        // given
        given(someMessage.getBody()).willReturn(envelopeJson());
        given(caseRetriever.retrieve(any(), any(), any())).willThrow(new RuntimeException());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isCompletedExceptionally()).isTrue();
    }

    private EventPublisher getDummyPublisher() {
        return (Envelope envelope) -> {
            //
        };
    }
}
