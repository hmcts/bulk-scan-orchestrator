package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ReceiverProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

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
        } catch (ServiceBusException | ConnectionException exc) {
            logger.error("Unable to read messages from queue", exc);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            logger.error("Unable to read messages from queue", exc);
        } catch (Exception e) {
            logger.error("Error processing message ID: {}", (msg != null) ? msg.getMessageId() : "none", e);
        }
    }

    private void process(IMessage msg) throws InterruptedException, java.util.concurrent.ExecutionException {
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
