package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.IntegrationTest;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@IntegrationTest
public class CleanupEnvelopesDlqTaskTest {

    @SpyBean
    private CleanupEnvelopesDlqTask cleanupEnvelopesDlqTask;

    /**
     * Verifies if the Dlq scheduler task is running for the configured interval.
     */
    @Test
    public void test_cleanup_dlq_scheduler_reads_dlq_message() {
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .ignoreExceptions()
            .untilAsserted(() -> {
                verify(cleanupEnvelopesDlqTask, atLeastOnce()).deleteMessagesInEnvelopesDlq();
            });
    }
}
