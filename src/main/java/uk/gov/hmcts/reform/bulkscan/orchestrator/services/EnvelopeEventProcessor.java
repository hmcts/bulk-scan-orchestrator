package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.bulkscanprocessorclient.client.BulkScanProcessorClient;

import java.util.concurrent.CompletableFuture;

@Component
public class EnvelopeEventProcessor implements IMessageHandler {
    private BulkScanProcessorClient bulkScanProcessorClient;

    public EnvelopeEventProcessor(BulkScanProcessorClient bulkScanProcessorClient) {
        this.bulkScanProcessorClient = bulkScanProcessorClient;
    }

    @Override
    public CompletableFuture<Void> onMessageAsync(IMessage message) {
        /*
         * NOTE: this is done here instead of offloading to the forkJoin pool "CompletableFuture.runAsync()"
         * because we probably should think about a threading model before doing this.
         * Maybe consider using Netflix's RxJava too (much simpler than CompletableFuture).
         */
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        try {
            process(message);
            completableFuture.complete(null);
        } catch (Throwable t) {
            completableFuture.completeExceptionally(t);
        }
        return completableFuture;
    }

    private void process(IMessage message) {
        bulkScanProcessorClient.getEnvelopeById(message.getMessageId());
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        //No exceptions expected until we use the azure API
    }
}
