package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.client.BulkScanProcessorClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.exceptions.ReadEnvelopeException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.ReceiverProvider;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

public class EnvelopeProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EnvelopeProcessor.class);

    private final ReceiverProvider receiverProvider;
    private final BulkScanProcessorClient bulkScanProcessorClient;

    public EnvelopeProcessor(
        ReceiverProvider receiverProvider,
        BulkScanProcessorClient bulkScanProcessorClient
    ) {
        this.receiverProvider = receiverProvider;
        this.bulkScanProcessorClient = bulkScanProcessorClient;
    }

    public void run() {
        try {
            IMessageReceiver msgReceiver = receiverProvider.get();
            IMessage msg = msgReceiver.receive();
            while (msg != null) {
                handle(msgReceiver, msg);
                msg = msgReceiver.receive();
            }
        } catch (InterruptedException | ServiceBusException | ConnectionException exc) {
            logger.error("Unable to read messages from queue", exc);
        }
    }

    private void handle(IMessageReceiver messageReceiver, IMessage msg)
        throws InterruptedException, ServiceBusException {

        try {
            Envelope envelope = bulkScanProcessorClient.getEnvelopeById(msg.getMessageId()); // NOPMD
            // TODO: use envelop data to interact with CCD
            messageReceiver.complete(msg.getLockToken());
        } catch (ReadEnvelopeException exc) {
            logger.error("Unable to read envelope with ID: " + exc.envelopeId, exc);
        }
    }
}
