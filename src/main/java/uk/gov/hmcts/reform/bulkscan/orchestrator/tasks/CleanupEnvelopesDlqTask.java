package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks;

import com.google.gson.Gson;
import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static java.nio.charset.Charset.defaultCharset;

/**
 * Deletes messages from envelopes Dead letter queue.
 */
@ConditionalOnProperty(value = "scheduling.task.delete-envelopes-dlq-messages.enabled")
public class CleanupEnvelopesDlqTask {

    private static final Logger log = LoggerFactory.getLogger(CleanupEnvelopesDlqTask.class);

    private final String connectionString;
    private final String queueName;
    private final Duration ttl;

    public CleanupEnvelopesDlqTask(
        @Value("${azure.servicebus.envelopes.connection-string}") String connectionString,
        @Value("${azure.servicebus.envelopes.queue-name}") String queueName,
        @Value("${scheduling.task.delete-envelopes-dlq-messages.ttl}") Duration ttl
    ) {
        this.connectionString = connectionString;
        this.queueName = queueName;
        this.ttl = ttl;
    }

    @Scheduled(cron = "${scheduling.task.delete-envelopes-dlq-messages.cron}")
    public void deleteMessagesInEnvelopesDlq() throws ServiceBusException, InterruptedException {
        log.info("Reading messages from envelopes Dead letter queue.");

        IMessageReceiver envelopesDlqReceiver = ClientFactory.createMessageReceiverFromConnectionStringBuilder(
            new ConnectionStringBuilder(connectionString, StringUtils.join(queueName, "/$deadletterqueue")),
            ReceiveMode.PEEKLOCK);

        while (envelopesDlqReceiver.receive() != null) {
            IMessage msg = envelopesDlqReceiver.receive(Duration.ofSeconds(2));
            if (canBeDeleted(msg)) {
                logMessage(msg);
                envelopesDlqReceiver.complete(msg.getLockToken());
            }
        }

        envelopesDlqReceiver.close();
    }

    private void logMessage(IMessage msg) {
        Gson gson = new Gson();
        Map msgContent = gson.fromJson(new String(msg.getBody(), defaultCharset()), Map.class);

        log.info(
            "Deleting message. ID: {} Envelope ID: {}, File name: {}, Jurisdiction: {}, Classification: {}, Case: {}",
            msg.getMessageId(),
            msgContent.get("id"),
            msgContent.get("zip_file_name"),
            msgContent.get("jurisdiction"),
            msgContent.get("classification"),
            msgContent.get("case_ref")
        );
    }

    private boolean canBeDeleted(IMessage message) {
        Instant createdTime = message.getEnqueuedTimeUtc();
        Instant cutoff = Instant.now().minus(this.ttl);

        return createdTime.isBefore(cutoff);
    }
}
