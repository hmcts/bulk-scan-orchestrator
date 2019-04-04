package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Deletes messages from envelopes Dead letter queue.
 */
@Component
@ConditionalOnProperty("scheduling.task.delete-envelopes-dlq-messages.enabled")
public class CleanupEnvelopesDlqTask {

    private static final Logger log = LoggerFactory.getLogger(CleanupEnvelopesDlqTask.class);

    Supplier<IMessageReceiver> dlqReceiverProvider;
    private final Duration ttl;

    public CleanupEnvelopesDlqTask(
        Supplier<IMessageReceiver> receiverProvider,
        @Value("${scheduling.task.delete-envelopes-dlq-messages.ttl}") Duration ttl
    ) {
        this.dlqReceiverProvider = receiverProvider;
        this.ttl = ttl;
    }

    @Scheduled(cron = "${scheduling.task.delete-envelopes-dlq-messages.cron}")
    public void deleteMessagesInEnvelopesDlq() throws ServiceBusException, InterruptedException {
        log.info("Reading messages from envelopes Dead letter queue.");
        IMessageReceiver messageReceiver = null;

        try {
            messageReceiver = dlqReceiverProvider.get();

            int completedCount = 0;
            IMessage message = messageReceiver.receive();
            while (message != null) {
                if (canBeCompleted(message)) {
                    logMessage(message);
                    messageReceiver.complete(message.getLockToken());
                    completedCount++;
                    log.info("Completed message from envelopes dlq. messageId: {}", message.getMessageId());
                }
                message = messageReceiver.receive();
            }

            log.info("Finished processing messages in envelopes dlq. Completed {} messages", completedCount);
        } catch (ConnectionException e) {
            log.error("Unable to connect to envelopes dead letter queue", e);
        } finally {
            if (messageReceiver != null) {
                try {
                    messageReceiver.close();
                } catch (ServiceBusException e) {
                    log.error("Error closing dlq connection", e);
                }
            }
        }
    }

    private void logMessage(IMessage msg) {
        try {
            Envelope envelope = EnvelopeParser.parse(msg.getBody());

            log.info(
                "Completing dlq message. messageId: {}, Envelope ID: {}, File name: {}, Jurisdiction: {},"
                    + " Classification: {}, Case: {}",
                msg.getMessageId(),
                envelope.id,
                envelope.zipFileName,
                envelope.jurisdiction,
                envelope.classification,
                envelope.caseRef
            );
        } catch (InvalidMessageException e) {
            // Not logging the exception as it prints the sensitive information from the envelope
            log.error("An error occurred while parsing the dlq message with messageId: {}",
                msg.getMessageId());
        }
    }

    private boolean canBeCompleted(IMessage message) {
        Instant createdTime = message.getEnqueuedTimeUtc();
        Instant cutoff = Instant.now().minus(this.ttl);
        boolean canBeCompleted = createdTime.isBefore(cutoff);

        log.info(
            "MessageId: {} Enqueued Time: {} ttl: {} can be completed? {}",
            message.getMessageId(),
            createdTime,
            this.ttl,
            canBeCompleted);

        return canBeCompleted;
    }
}
