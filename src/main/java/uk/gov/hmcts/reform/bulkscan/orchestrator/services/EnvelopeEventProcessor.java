package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.events.EventPublisherContainer;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.CompletableFuture;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser.parse;

@Service
public class EnvelopeEventProcessor implements IMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventProcessor.class);
    public static final String EXCEPTION_RECORD_CASE_TYPE = "ExceptionRecord";

    private final EventPublisherContainer eventPublisherContainer;

    public EnvelopeEventProcessor(EventPublisherContainer eventPublisherContainer) {
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
        Envelope envelope = parse(message.getBody());
        EventPublisher eventPublisher = eventPublisherContainer.getPublisher(envelope);

        if (eventPublisher != null) {
            eventPublisher.publish(envelope, getCaseTypeId(null));
        } else {
            log.info(
                "Skipped processing of envelope ID {} for case {} - classification {} not handled yet",
                envelope.id,
                envelope.caseRef,
                envelope.classification
            );
        }
    }

    private String getCaseTypeId(CaseDetails theCase) {
        return theCase == null ? EXCEPTION_RECORD_CASE_TYPE : theCase.getCaseTypeId();
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        log.error("Error while handling message at stage: " + phase, exception);
    }
}
