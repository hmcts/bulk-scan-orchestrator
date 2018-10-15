package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.IMessageReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.MessageReceiverFactory;

@Service
@ConditionalOnProperty(value = "scheduling.enabled", matchIfMissing = true)
public class MessageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MessageProcessor.class);

    private final MessageReceiverFactory messageReceiverFactory;
    private final IMessageHandler envelopeProcessor;
    private final IMessageReceiver msgReceiver;

    MessageProcessor(MessageReceiverFactory factory, IMessageHandler envelopeProcessor) {
        this.messageReceiverFactory = factory;
        this.envelopeProcessor = envelopeProcessor;
        this.msgReceiver = messageReceiverFactory.create();
    }

    @Scheduled(fixedDelayString = "${queue.read-interval}")
    public void run() {
        logger.info("Started processing queue messages.");

        IMessage msg = null;
        int processedMessagesCount = 0;

        try {
            while ((msg = msgReceiver.receive()) != null) {
                envelopeProcessor.onMessageAsync(msg).get();
                msgReceiver.complete(msg.getLockToken());
                processedMessagesCount++;
            }
            logger.info("Message processing complete. Processed {} messages", processedMessagesCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("interrupted", e);
        } catch (Throwable throwable) {
            logger.error("Message processing exception msgId:{}", getMessageId(msg), throwable);
        }
    }

    private String getMessageId(IMessage msg) {
        return (msg != null) ? msg.getMessageId() : "none";
    }
}
