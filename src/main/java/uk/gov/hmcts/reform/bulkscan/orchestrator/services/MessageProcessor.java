package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.IMessageReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ReceiverProvider;

import java.util.concurrent.ExecutionException;

@Service
public class MessageProcessor {
    public static final String TEST_MSG_LABEL = "test";

    private static final Logger logger = LoggerFactory.getLogger(MessageProcessor.class);

    private final ReceiverProvider receiverProvider;
    private final IMessageHandler envelopeProcessor;

    public MessageProcessor(ReceiverProvider receiverProvider, IMessageHandler envelopeProcessor) {
        this.receiverProvider = receiverProvider;
        this.envelopeProcessor = envelopeProcessor;
    }

    @Scheduled(fixedDelayString = "${queue.read-interval}")
    public void run() {
        IMessage msg = null;
        try {
            IMessageReceiver msgReceiver = receiverProvider.get();
            while ((msg = msgReceiver.receive()) != null) {
                process(msg);
                msgReceiver.complete(msg.getLockToken());
            }
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

    private void process(IMessage msg) throws ExecutionException, InterruptedException {
        if (isTestMessage(msg)) {
            logger.info("Received test message, messageId: {}", msg.getMessageId());
        } else {
            envelopeProcessor.onMessageAsync(msg).get();
        }
    }

    private boolean isTestMessage(IMessage msg) {
        return TEST_MSG_LABEL.equals(msg.getLabel());
    }

}
