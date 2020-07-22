package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.QueueProcessingReadinessChecker;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.LogInAttemptRejectedException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeMessageProcessor;

@Service
@ConditionalOnProperty(value = "scheduling.task.consume-envelopes-queue.enabled", matchIfMissing = true)
public class EnvelopesQueueConsumeTask {

    private static final Logger log = LoggerFactory.getLogger(EnvelopesQueueConsumeTask.class);
    private static final String TASK_NAME = "consume-envelopes-queue";

    private final EnvelopeMessageProcessor envelopeMessageProcessor;
    private final QueueProcessingReadinessChecker processingReadinessChecker;

    public EnvelopesQueueConsumeTask(
        EnvelopeMessageProcessor envelopeMessageProcessor,
        QueueProcessingReadinessChecker processingReadinessChecker
    ) {
        this.envelopeMessageProcessor = envelopeMessageProcessor;
        this.processingReadinessChecker = processingReadinessChecker;
    }

    @Scheduled(fixedDelay = 1000)
    public void consumeMessages() {
        log.info("Started {} job", TASK_NAME);

        try {
            boolean queueMayHaveMessages = true;

            while (queueMayHaveMessages && isReadyForConsumingMessages()) {
                queueMayHaveMessages = envelopeMessageProcessor.processNextMessage();
            }
        } catch (InterruptedException exception) {
            logTaskError(exception);
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            logTaskError(exception);
        }

        log.info("Finished {} job", TASK_NAME);
    }

    private boolean isReadyForConsumingMessages() throws LogInAttemptRejectedException {
        // TODO: add S2S and IDAM health checks
        return processingReadinessChecker.isNoLogInAttemptRejectedByIdam();
    }

    private void logTaskError(Exception exception) {
        log.error("An error occurred when running the 'consume messages' task", exception);
    }
}
