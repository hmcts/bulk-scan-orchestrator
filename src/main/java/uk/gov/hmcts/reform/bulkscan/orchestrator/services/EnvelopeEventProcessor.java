package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.google.common.base.Strings;
import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.CompletableFuture;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser.parse;

@Service
public class EnvelopeEventProcessor implements IMessageHandler {

    private final CaseRetriever caseRetriever;

    public EnvelopeEventProcessor(CaseRetriever caseRetriever) {
        this.caseRetriever = caseRetriever;
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
        CaseDetails theCase = Strings.isNullOrEmpty(envelope.caseRef)
            ? null
            : retrieveCase(envelope.jurisdiction, envelope.caseRef);

        // - create record from envelope and case
        // - supply it to ccd event publisher
    }

    private CaseDetails retrieveCase(String jurisdiction, String caseRef) {
        CcdAuthenticator authenticator = caseRetriever.authenticate(jurisdiction);
        return caseRetriever.retrieve(jurisdiction, caseRef, authenticator);
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        //No exceptions expected until we use the azure API
    }
}
