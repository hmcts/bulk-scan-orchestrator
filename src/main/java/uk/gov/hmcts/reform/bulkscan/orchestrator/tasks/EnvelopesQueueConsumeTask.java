package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.QueueProcessingReadinessChecker;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.LogInAttemptRejectedException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeEventProcessor;

@Service
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
    public void consumeMessages() {
        log.info("Started the job consuming envelope messages");

        try {
            boolean queueMayHaveMessages = true;

            while (queueMayHaveMessages && isReadyForConsumingMessages()) {
                queueMayHaveMessages = envelopeEventProcessor.processNextMessage();
            }

            log.info("Finished the job consuming envelope messages");
        } catch (InterruptedException exception) {
            logTaskError(exception);
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            logTaskError(exception);
        }
    }

    private boolean isReadyForConsumingMessages() throws LogInAttemptRejectedException {
        // TODO: add S2S and IDAM health checks
        return processingReadinessChecker.isNoLogInAttemptRejectedByIdam();
    }

    private void logTaskError(Exception exception) {
        log.error("An error occurred when running the 'consume messages' task", exception);
    }
}
