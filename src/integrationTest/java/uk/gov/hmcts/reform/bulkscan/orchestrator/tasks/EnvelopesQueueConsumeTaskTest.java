package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import static org.mockito.Mockito.verify;

@IntegrationTest
public class EnvelopesQueueConsumeTaskTest {

    @SpyBean
    private EnvelopesQueueConsumeTask envelopesQueueConsumeTask;

    @MockBean
    private ServiceBusProcessorClient serviceBusProcessorClient;

    @Test
    public void should_start_ServiceBusProcessorClient() {
        verify(serviceBusProcessorClient).start();
    }

}
