package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.concurrent.CompletableFuture;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.CompletableHelper.completeRunnable;

@Service
public class EnvelopeEventProcessor implements CompletableFutureWrapper, IMessageHandler {
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

    @Override
    public void process(IMessage message) {
        Envelope envelope = EnvelopeParser.parse(message.getBody());
        CcdAuthInfo authInfo = authenticator.authenticateForJurisdiction(envelope.jurisdiction);
        CaseDetails workerCase = caseRetriever.retrieve(authInfo, envelope.jurisdiction, envelope.caseRef);
        log.info("Found worker case: {}:{}:{}", workerCase.getJurisdiction(), workerCase.getCaseTypeId(), workerCase.getId());
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        //Left empty for now.
    }
}
