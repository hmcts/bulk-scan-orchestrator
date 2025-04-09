package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.azure.core.util.IterableStream;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.ConnectionException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.InvalidMessageException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Deletes messages from envelopes Dead letter queue.
 */
@Component
@ConditionalOnProperty("scheduling.task.delete-envelopes-dlq-messages.enabled")
@ConditionalOnExpression("!${jms.enabled}")
public class CleanupEnvelopesDlqTask {

    private static final Logger log = LoggerFactory.getLogger(CleanupEnvelopesDlqTask.class);
    private static final String TASK_NAME = "delete-envelopes-dlq-messages";

    Supplier<ServiceBusReceiverClient> dlqReceiverProvider;
    private final Duration ttl;

    public CleanupEnvelopesDlqTask(
        Supplier<ServiceBusReceiverClient> receiverProvider,
        @Value("${scheduling.task.delete-envelopes-dlq-messages.ttl}") Duration ttl
    ) {
        this.dlqReceiverProvider = receiverProvider;
        this.ttl = ttl;
    }

    @Scheduled(cron = "${scheduling.task.delete-envelopes-dlq-messages.cron}")
    public void deleteMessagesInEnvelopesDlq() throws ServiceBusException, InterruptedException {
        log.info("Started {} job", TASK_NAME);
        ServiceBusReceiverClient messageReceiver = null;

        try {
            messageReceiver = dlqReceiverProvider.get();
            ServiceBusReceivedMessage message = null;
            int completedCount = 0;
            do {
                message = null;
                IterableStream<ServiceBusReceivedMessage> messages =
                    messageReceiver.receiveMessages(1, Duration.ofSeconds(1));
                var opt = messages.stream().findFirst();
                if (opt.isPresent()) {
                    message = opt.get();
                    messageReceiver.renewMessageLock(message);
                    if (canBeCompleted(message)) {
                        logMessage(message);
                        messageReceiver.complete(message);
                        completedCount++;
                        log.info(
                            "Completed message from envelopes dlq. messageId: {} Current time: {}",
                            message.getMessageId(),
                            Instant.now()
                        );
                    } else {
                        // just continue, lock on the current msg will expire automatically
                        log.info(
                            "Leaving message on dlq, ttl has not passed yet. Message id: {}",
                            message.getMessageId()
                        );
                    }
                }
            } while (message != null);

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
        log.info("Finished {} job", TASK_NAME);
    }

    private void logMessage(ServiceBusReceivedMessage msg) {
        try {
            Envelope envelope = EnvelopeParser.parse(msg.getBody().toBytes());

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
            log.error(
                "An error occurred while parsing the dlq message with messageId: {}",
                msg.getMessageId()
            );
        }
    }

    private boolean canBeCompleted(ServiceBusReceivedMessage message) {
        Instant cutoff = Instant.now().minus(this.ttl);
        Map<String, Object> messageProperties = message.getApplicationProperties();

        String deadLetteredAtStr =
            messageProperties == null
                ? null
                : (String) messageProperties.get("deadLetteredAt");

        if (deadLetteredAtStr != null) {
            Instant deadLetteredAt = Instant.parse(deadLetteredAtStr);

            log.info(
                "Checking if DLQ message can be completed. "
                    + "MessageId: {}, dead lettered time: {}, ttl: {}, current time: {}",
                message.getMessageId(),
                deadLetteredAt,
                this.ttl,
                Instant.now()
            );
            return deadLetteredAt.isBefore(cutoff);
        }
        return false;
    }
}
