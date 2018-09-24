package uk.gov.hmcts.reform.bulkscan.orchestrator.services;

import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.Jurisdiction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.Credential;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.util.concurrent.CompletableFuture;

@Service
public class EnvelopeEventProcessor implements IMessageHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final AuthTokenGenerator s2sTokenGenerator;
    private final IdamClient idamClient;
    private JurisdictionToUserMapping users;
    private static final Logger log = LoggerFactory.getLogger(EnvelopeEventProcessor.class);

    public EnvelopeEventProcessor(CoreCaseDataApi coreCaseDataApi,
                                  AuthTokenGenerator s2sTokenGenerator,
                                  IdamClient idamClient,
                                  JurisdictionToUserMapping users) {
        this.users = users;
        this.coreCaseDataApi = coreCaseDataApi;
        this.s2sTokenGenerator = s2sTokenGenerator;
        this.idamClient = idamClient;
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
        String sscsToken = s2sTokenGenerator.generate();
        Jurisdiction jurisdiction = Jurisdiction.SSCS;
        Credential user = users.getUser(jurisdiction);
        Envelope envelope = EnvelopeParser.parse(message.getBody());
        String userToken = idamClient.authenticateUser(user.getUsername(), user.getPassword());
        try {
            CaseDetails workerCase = coreCaseDataApi.readForCaseWorker(userToken,
                sscsToken, user.getUsername(),
                jurisdiction.name(),
                "Bulk_Scanned",
                envelope.caseRef);
            log.info("Retrieved case {}:{}:{}", workerCase.getJurisdiction(), workerCase.getCaseTypeId(), workerCase.getId());
        } catch (FeignException e) {
            log.error("Failed to read Case status: {}", e.status(), e);
        }
    }

    @Override
    public void notifyException(Throwable exception, ExceptionPhase phase) {
        //No exceptions expected until we use the azure API
    }
}
