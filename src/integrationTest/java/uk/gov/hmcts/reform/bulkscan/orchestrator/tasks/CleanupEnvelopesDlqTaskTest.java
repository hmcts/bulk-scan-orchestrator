package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.microsoft.azure.servicebus.IMessageReceiver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

@RunWith(SpringRunner.class)
@IntegrationTest
public class CleanupEnvelopesDlqTaskTest {

    @Autowired
    Supplier<IMessageReceiver> dlqReceiverProvider;

    private CleanupEnvelopesDlqTask dlqTask;

    @Value("${scheduling.task.delete-envelopes-dlq-messages.ttl}")
    private Duration ttl;

    @Before
    public void setUp() {
        dlqTask = new CleanupEnvelopesDlqTask(dlqReceiverProvider, ttl);
    }

    @Test
    public void test_cleanup_dlq_scheduler_reads_dlq_message() {
        IMessageReceiver iMessageReceiver = dlqReceiverProvider.get();
        await()
            .atMost(15, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                verify(iMessageReceiver).receive();
                verify(iMessageReceiver).close();
                return true;
            });
    }
}
