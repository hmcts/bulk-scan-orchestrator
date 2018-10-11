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
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.authorisation.generators.ServiceAuthTokenGenerator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.IdamClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.TestHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.idam.JurisdictionToUserMapping;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class AttachSupplementaryEvidenceTest {

    private IMessageReceiver readClient;
    private QueueClient writeClient;
    private int readInterval;

    private String s2sUrl;
    private String s2sName;
    private String s2sSecret;

    private CaseRetriever caseRetriever;

    @Value("${idam-url}")
    protected String idamUrl;

    @Value("${idam-user-email}")
    protected String idamUserEmail;

    @Value("${idam-password}")
    protected String idamPassword;

    @Value("${idam-client-id}")
    protected String idamClientId;

    @Value("${idam-client-secret}")
    protected String idamClientSecret;

    @Value("${idam-redirect-uri}")
    protected String idamRedirectUri;

    @Value("${idam.s2s-auth.url}")
    protected String s2sAuthUrl;

    @Value("${core-case-data.url}")
    protected String ccdUrl;

    @Value("${use-idam-testing-support}")
    protected boolean useIdamTestingSupport;

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

        IdamClient idamClient = createIdamClient();

        ServiceAuthorisationApi serviceAuthorisationApi = Feign
            .builder()
            .target(ServiceAuthorisationApi.class, s2sAuthUrl);

        ServiceAuthTokenGenerator tokenGenerator = new ServiceAuthTokenGenerator(s2sSecret, s2sName, serviceAuthorisationApi);

//        CcdAuthenticatorFactory factory =new CcdAuthenticatorFactory(tokenGenerator, idamClient, new JurisdictionToUserMapping());

        this.s2sUrl = conf.getString("test-s2s-url");
        this.s2sName = conf.getString("test-s2s-name");
        this.s2sSecret = conf.getString("test-s2s-secret");

        String s2sToken = TestHelper.s2sSignIn(s2sName, s2sSecret, s2sUrl);

        JurisdictionToUserMapping jurisdictionToUserMapping = new JurisdictionToUserMapping();


        CoreCaseDataApi coreCaseDataApi;

//        this.caseRetriever = new CaseRetriever(factory, coreCaseDataApi);

    }

    private IdamClient createIdamClient() {
        return new IdamClient(
            idamUrl,
            idamUserEmail,
            idamPassword,
            idamClientId,
            idamClientSecret,
            idamRedirectUri
        );
    }

    @Test
    public void should_attach_supplementary_evidence_to_the_case() throws Exception {
        // given
        //create a case for testing
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
