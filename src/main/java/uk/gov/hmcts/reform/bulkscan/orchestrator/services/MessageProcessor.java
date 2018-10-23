package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.IMessageReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.MessageReceiverFactory;

//@Service
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

        int processedMessagesCount = 0;
        int failedMessageCount = 0;

        try {
            IMessage msg = null;
            while ((msg = msgReceiver.receive()) != null) {
                if (tryProcessMessage(msg)) {
                    msgReceiver.complete(msg.getLockToken());
                } else {
                    failedMessageCount++;
                }

                processedMessagesCount++;
            }

            logProcessingCompletion(processedMessagesCount, failedMessageCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("interrupted", e);
        } catch (Throwable throwable) {
            logger.error("Message processing job failed", throwable);
        }
    }

    private boolean tryProcessMessage(IMessage message) {
        try {
            envelopeProcessor.onMessageAsync(message).get();
            return true;
        } catch (Exception ex) {
            logger.error("Failed to process message with Id: {}", getMessageId(message), ex);
            return false;
        }
    }

    private void logProcessingCompletion(int processedMessagesCount, int failedMessageCount) {
        logger.info(
            "Message processing complete. Successful: {}, Failed: {}, Total: {})",
            processedMessagesCount - failedMessageCount,
            failedMessageCount,
            processedMessagesCount
        );
    }

    private String getMessageId(IMessage msg) {
        return (msg != null) ? msg.getMessageId() : "none";
    }
}
