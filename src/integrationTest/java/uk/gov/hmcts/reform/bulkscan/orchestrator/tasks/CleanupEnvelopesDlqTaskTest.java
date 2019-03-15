package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.microsoft.azure.servicebus.IMessageReceiver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@IntegrationTest
public class CleanupEnvelopesDlqTaskTest {

    @Autowired
    private Supplier<IMessageReceiver> dlqReceiverProvider;

    @Value("${scheduling.task.delete-envelopes-dlq-messages.ttl}")
    private Duration ttl;

    /**
     * Verifies if the Dlq scheduler task is running for the configured interval.
     */
    @Test
    public void test_cleanup_dlq_scheduler_reads_dlq_message() {
        IMessageReceiver messageReceiver = dlqReceiverProvider.get();

        await()
            .atMost(5, TimeUnit.SECONDS)
            .ignoreExceptions()
            .until(() -> {
                verify(messageReceiver).receive();
                verify(messageReceiver).close();
                return true;
            });
    }
}
