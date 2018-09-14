package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.client.BulkScanProcessorClient;

import java.util.concurrent.CompletableFuture;

@Component
public class EnvelopeProcessor implements IMessageHandler {
    private BulkScanProcessorClient bulkScanProcessorClient;

    public EnvelopeProcessor(BulkScanProcessorClient bulkScanProcessorClient) {
        this.bulkScanProcessorClient = bulkScanProcessorClient;
    }

    @Override
    public CompletableFuture<Void> onMessageAsync(IMessage message) {
        return CompletableFuture.runAsync(() -> process(message));
    }

    private void process(IMessage message) {
        bulkScanProcessorClient.getEnvelopeById(message.getMessageId());
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        //No exceptions expected untill we use the azure API
    }
}
