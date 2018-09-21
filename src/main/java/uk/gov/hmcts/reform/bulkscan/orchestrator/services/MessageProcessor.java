package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.IMessageReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ReceiverProvider;

@Service
public class MessageProcessor {

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

                envelopeProcessor.onMessageAsync(msg).get();

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
}
