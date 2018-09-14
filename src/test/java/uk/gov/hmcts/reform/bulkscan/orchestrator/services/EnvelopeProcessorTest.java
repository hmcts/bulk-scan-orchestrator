package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.client.BulkScanProcessorClient;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnvelopeProcessorTest {

    @Mock
    BulkScanProcessorClient bulkScanProcessorClient;

    private EnvelopeProcessor envelopeProcessor;

    @Mock
    private IMessage someMessage;
    private static final String MSG_ID = "hello!";

    @BeforeEach
    void before() {
        envelopeProcessor = new EnvelopeProcessor(bulkScanProcessorClient);
    }

    @Test
    @DisplayName("should use queue message id to read envelope")
    void readEnvelope() throws ExecutionException, InterruptedException {
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
    @DisplayName("empty test for coverage of the exception method.")
    void notifyException() {
        // when
        envelopeProcessor.notifyException(null, null);
    }


}
