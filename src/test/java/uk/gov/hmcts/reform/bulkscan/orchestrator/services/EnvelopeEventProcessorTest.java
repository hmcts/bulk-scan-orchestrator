package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.client.BulkScanProcessorClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class EnvelopeEventProcessorTest {

    @Mock
    BulkScanProcessorClient bulkScanProcessorClient;

    private EnvelopeEventProcessor processor;

    @Mock
    private IMessage someMessage;
    private static final String MSG_ID = "hello!";

    @Before
    public void before() {
        processor = new EnvelopeEventProcessor(bulkScanProcessorClient);
        given(someMessage.getMessageId()).willReturn(MSG_ID);
    }

    @Test
    public void should_use_queue_message_id_to_read_envelope() throws ExecutionException, InterruptedException {

        // when
        processor.onMessageAsync(someMessage).get();

        // then
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(bulkScanProcessorClient).getEnvelopeById(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isEqualTo(MSG_ID);
    }

    @Test
    public void should_capture_exception_in_completable() {
        //given
        given(bulkScanProcessorClient.getEnvelopeById(MSG_ID)).willThrow(new RuntimeException("completable"));

        // when
        CompletableFuture<Void> future = processor.onMessageAsync(someMessage);

        //then
        assertThat(future.isCompletedExceptionally()).isTrue();
        assertThat(future.isDone()).isTrue();

        Throwable thrown = catchThrowable(future::get);
        assertThat(thrown).isInstanceOf(ExecutionException.class);
        assertThat(thrown.getCause()).isInstanceOf(RuntimeException.class);
        assertThat(thrown.getCause()).hasMessage("completable");

    }

    @Test
    public void should_not_do_anything_in_notify() {
        // when
        processor.notifyException(null, null);
    }
}
