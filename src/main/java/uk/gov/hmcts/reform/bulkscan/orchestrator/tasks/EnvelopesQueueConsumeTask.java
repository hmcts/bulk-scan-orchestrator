package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.EnvelopeEventProcessor;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.QueueProcessingReadinessChecker;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.AccountLockedException;

@ConditionalOnProperty(value = "scheduling.task.consume-envelopes-queue.enabled", matchIfMissing = true)
public class EnvelopesQueueConsumeTask {

    private static final Logger log = LoggerFactory.getLogger(EnvelopesQueueConsumeTask.class);

    private final EnvelopeEventProcessor envelopeEventProcessor;
    private final QueueProcessingReadinessChecker processingReadinessChecker;

    public EnvelopesQueueConsumeTask(
        EnvelopeEventProcessor envelopeEventProcessor,
        QueueProcessingReadinessChecker processingReadinessChecker
    ) {
        this.envelopeEventProcessor = envelopeEventProcessor;
        this.processingReadinessChecker = processingReadinessChecker;
    }

    @Scheduled(fixedDelay = 1000)
    public void consumeMessages() throws ServiceBusException, InterruptedException {
        try {
            boolean queueMayHaveMessages = true;

            while (queueMayHaveMessages && isReadyForConsumingMessages()) {
                queueMayHaveMessages = envelopeEventProcessor.processNextMessage();
            }
        } catch (Exception e) {
            log.error("An error occurred when running the 'consume messages' task", e);
        }
    }

    private boolean isReadyForConsumingMessages() throws AccountLockedException {
        // TODO: add S2S and IDAM health checks
        return processingReadinessChecker.isNoAccountLockedInIdam();
    }
}
