package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import static org.mockito.Mockito.verify;

@IntegrationTest
@SpringBootTest(properties = {"scheduling.task.check-envelopes-queue.enabled=true"})
public class EnvelopesQueueConsumeTaskTest {

    @MockitoSpyBean
    private EnvelopesQueueConsumeTask envelopesQueueConsumeTask;

    @MockitoBean
    private ServiceBusProcessorClient serviceBusProcessorClient;

    @Test
    public void should_start_ServiceBusProcessorClient() {
        verify(serviceBusProcessorClient).start();
    }

}
