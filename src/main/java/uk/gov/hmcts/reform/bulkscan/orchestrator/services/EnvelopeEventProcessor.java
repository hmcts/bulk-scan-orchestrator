package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.google.common.base.Strings;
import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisherContainer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser.parse;

@Service
public class EnvelopeEventProcessor implements IMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventProcessor.class);

    private final CaseRetriever caseRetriever;

    private final EventPublisherContainer eventPublisherContainer;

    public EnvelopeEventProcessor(CaseRetriever caseRetriever, EventPublisherContainer eventPublisherContainer) {
        this.caseRetriever = caseRetriever;
        this.eventPublisherContainer = eventPublisherContainer;
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
        log.info("Started processing message with ID {}", message.getMessageId());
        try {
            Envelope envelope = parse(message.getBody());
            log.info(
                "Parsed message with ID {}. Envelope ID: {}, File name: {}. Jurisdiction: {}",
                message.getMessageId(),
                envelope.id,
                envelope.zipFileName,
                envelope.jurisdiction
            );

            Supplier<CaseDetails> caseRetrieval = () -> Strings.isNullOrEmpty(envelope.caseRef)
                ? null
                : caseRetriever.retrieve(envelope.jurisdiction, envelope.caseRef);
            EventPublisher eventPublisher = eventPublisherContainer.getPublisher(
                envelope.classification,
                caseRetrieval
            );

            eventPublisher.publish(envelope);

            log.info("Processed message with ID {}", message.getMessageId());
        } catch (Exception ex) {
            throw new MessageProcessingException(
                "Failed to process message with ID " + message.getMessageId(),
                ex
            );
        }
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        log.error("Error while handling message at stage: " + phase, exception);
    }
}
