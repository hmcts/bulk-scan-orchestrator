package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.SupplementaryEvidenceCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Classification;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.envelopeJson;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData.objectMapper;

@RunWith(MockitoJUnitRunner.class)
public class EnvelopeEventProcessorTest {
    @Mock
    private IMessage someMessage;
    @Mock
    private SupplementaryEvidenceCreator supplementaryEvidenceCreator;

    private EnvelopeEventProcessor processor;

    @Before
    public void before() throws Exception {
        processor = new EnvelopeEventProcessor(supplementaryEvidenceCreator);
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
        given(someMessage.getBody())
            .willReturn(envelopeJson(Classification.SUPPLEMENTARY_EVIDENCE));

        willThrow(new RuntimeException()).given(supplementaryEvidenceCreator).createSupplementaryEvidence(any());

        // when
        CompletableFuture<Void> result = processor.onMessageAsync(someMessage);

        // then
        assertThat(result.isCompletedExceptionally()).isTrue();
    }

    @Test
    public void should_handle_messages_with_supplementary_evidence_classification() throws Exception {
        // given
        given(someMessage.getBody())
            .willReturn(envelopeJson(Classification.SUPPLEMENTARY_EVIDENCE));

        // when
        processor.onMessageAsync(someMessage);

        // then
        ArgumentCaptor<Envelope> envelopeCaptor = ArgumentCaptor.forClass(Envelope.class);
        verify(supplementaryEvidenceCreator).createSupplementaryEvidence(envelopeCaptor.capture());

        Envelope expectedEnvelope = objectMapper.readValue(someMessage.getBody(), Envelope.class);

        assertThat(envelopeCaptor.getValue())
            .isEqualToComparingFieldByFieldRecursively(expectedEnvelope);
    }

    @Test
    public void should_not_process_messages_with_new_application_classification() throws Exception {
        // given
        given(someMessage.getBody()).willReturn(envelopeJson(Classification.NEW_APPLICATION));

        // when
        processor.onMessageAsync(someMessage);

        verifyNoMoreInteractions(supplementaryEvidenceCreator);
    }

    @Test
    public void should_not_process_messages_with_exception_classification() throws Exception {
        // given
        given(someMessage.getBody()).willReturn(envelopeJson(Classification.EXCEPTION));

        // when
        processor.onMessageAsync(someMessage);

        verifyNoMoreInteractions(supplementaryEvidenceCreator);
    }
}
