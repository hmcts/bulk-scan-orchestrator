package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.CompletableFuture;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.CompletableHelper.completeRunnable;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser.parse;

@Service
public class EnvelopeEventProcessor implements IMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventProcessor.class);

    private final CcdAuthService authenticator;
    private final CcdCaseRetriever caseRetriever;

    public EnvelopeEventProcessor(CcdCaseRetriever caseRetriever,
                                  CcdAuthService authenticator) {
        this.caseRetriever = caseRetriever;
        this.authenticator = authenticator;
    }

    @Override
    public CompletableFuture<Void> onMessageAsync(IMessage message) {
        return completeRunnable(() -> process(message));
    }

    private void process(IMessage message) {
        Envelope envelope = parse(message.getBody());
        CcdAuthInfo authInfo = authenticator.authenticateForJurisdiction(envelope.jurisdiction);
        CaseDetails theCase = caseRetriever.retrieve(authInfo, envelope.caseRef);
        log.info("Found worker case: {}:{}:{}",
            theCase.getJurisdiction(),
            theCase.getCaseTypeId(),
            theCase.getId());
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        // Left empty on purpose
    }
}
