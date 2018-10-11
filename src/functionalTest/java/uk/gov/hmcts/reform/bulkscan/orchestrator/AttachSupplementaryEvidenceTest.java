package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import feign.Feign;
import org.awaitility.Duration;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.ServiceAuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.TestHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CcdAuthenticatorFactory;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.idam.client.IdamClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class AttachSupplementaryEvidenceTest {

    private IMessageReceiver readClient;
    private QueueClient writeClient;
    private int readInterval;

    private String coreCaseDataUrl;
    private String s2sAuthUrl;
    private String s2sName;
    private String s2sSecret;
    private String idamUrl;

    private CaseRetriever caseRetriever;

    @Before
    public void setUp() throws Exception {
        Config conf = ConfigFactory.load();

        this.readInterval = conf.getInt("queue.read-interval");

        this.readClient = ClientFactory.createMessageReceiverFromConnectionString(
            conf.getString("queue.conn-strings.read"),
            ReceiveMode.PEEKLOCK
        );

        this.writeClient = new QueueClient(
            new ConnectionStringBuilder(conf.getString("queue.conn-strings.write")),
            ReceiveMode.PEEKLOCK
        );

        this.s2sAuthUrl = conf.getString("idam.s2s-auth.url");
        this.s2sName = conf.getString("idam.s2s-auth.name");
        this.s2sSecret = conf.getString("idam.s2s-auth.secret");

        this.coreCaseDataUrl = conf.getString("core-case-data.api.url");
        this.idamUrl = conf.getString("idam.api.url");

        CcdAuthenticatorFactory factory = getCcdAuthenticatorFactory();

        CoreCaseDataApi coreCaseDataApi = getCoreCaseDataApi();

        this.caseRetriever = new CaseRetriever(factory, coreCaseDataApi);
    }

    private CoreCaseDataApi getCoreCaseDataApi() {
        return Feign
            .builder()
            .target(CoreCaseDataApi.class, coreCaseDataUrl);
    }

    @NotNull
    private CcdAuthenticatorFactory getCcdAuthenticatorFactory() {

        ServiceAuthorisationApi serviceAuthorisationApi = Feign
            .builder()
            .target(ServiceAuthorisationApi.class, s2sAuthUrl);

        IdamClient idam = Feign
            .builder()
            .target(IdamClient.class, idamUrl);

        ServiceAuthTokenGenerator tokenGenerator = new ServiceAuthTokenGenerator(s2sSecret, s2sName, serviceAuthorisationApi);

        return new CcdAuthenticatorFactory(tokenGenerator, idam, new JurisdictionToUserMapping());
    }

    @Test
    public void should_attach_supplementary_evidence_to_the_case() throws Exception {
        // given
        //TODO: create a case for testing
        Message message = new Message();
        message.setMessageId(UUID.randomUUID().toString());
        byte[] envelopeJson = SampleData.envelopeJson();
        message.setBody(envelopeJson);

        // when
        writeClient.send(message);

        // then
        await("Supplementary evidence is attached to the case in ccd")
            .atMost(45, TimeUnit.SECONDS)
            .pollInterval(Duration.TWO_SECONDS)
            .until(() -> caseUpdated(envelopeJson));
    }

    private Boolean caseUpdated(byte[] envelopeJson) {

        Envelope envelope = EnvelopeParser.parse(envelopeJson);

        CaseDetails updatedCaseDetails = caseRetriever.retrieve(envelope.jurisdiction, envelope.caseRef);

        List<Document> updatedScannedDocuments = TestHelper.getScannedDocuments(updatedCaseDetails);
        List<Document> scannedDocuments = envelope.documents;
        return scannedDocuments.stream().allMatch(document -> updatedScannedDocuments.contains(document));
    }
}
