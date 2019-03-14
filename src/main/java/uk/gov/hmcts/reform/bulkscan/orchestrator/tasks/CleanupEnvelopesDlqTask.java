package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Deletes messages from envelopes Dead letter queue.
 */
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

            IMessage message = messageReceiver.receive();
            while (message != null) {
                if (canBeDeleted(message)) {
                    logMessage(message);
                    messageReceiver.complete(message.getLockToken());
                }
                message = messageReceiver.receive();
            }

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
        Envelope envelope = EnvelopeParser.parse(msg.getBody());

        log.info(
            "Deleting message. ID: {} Envelope ID: {}, File name: {}, Jurisdiction: {}, Classification: {}, Case: {}",
            msg.getMessageId(),
            envelope.id,
            envelope.zipFileName,
            envelope.jurisdiction,
            envelope.classification,
            envelope.caseRef
        );
    }

    private boolean canBeDeleted(IMessage message) {
        Instant createdTime = message.getEnqueuedTimeUtc();
        Instant cutoff = Instant.now().minus(this.ttl);

        return createdTime.isBefore(cutoff);
    }
}
