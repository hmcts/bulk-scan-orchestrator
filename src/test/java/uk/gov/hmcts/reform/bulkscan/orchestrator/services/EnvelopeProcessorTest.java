package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.client.BulkScanProcessorClient;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class EnvelopeProcessorTest {

    @Mock
    BulkScanProcessorClient bulkScanProcessorClient;

    private EnvelopeProcessor envelopeProcessor;

    @Mock
    private IMessage someMessage;
    private static final String MSG_ID = "hello!";

    @Before
    public void before() {
        envelopeProcessor = new EnvelopeProcessor(bulkScanProcessorClient);
    }

    @Test
    public void should_use_queue_message_id_to_read_envelope() throws ExecutionException, InterruptedException {
        //given
        given(someMessage.getMessageId()).willReturn(MSG_ID);

        // when
        envelopeProcessor.onMessageAsync(someMessage).get();

        // then
        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(bulkScanProcessorClient).getEnvelopeById(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isEqualTo(MSG_ID);
    }

    @Test
    public void should_not_do_anything_in_notify() {
        // when
        envelopeProcessor.notifyException(null, null);
    }


}
