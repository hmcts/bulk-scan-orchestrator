package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.awaitility.Duration;
import org.junit.Before;
import org.junit.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.TestHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.EnvelopeParser;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Document;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class AttachSupplementaryEvidenceTest {

    private IMessageReceiver readClient;
    private QueueClient writeClient;
    private int readInterval;

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
        CaseDetails caseDetails = caseRetriever.retrieve(envelope.jurisdiction, envelope.caseRef);

        List<Document> updatedScannedDocuments = TestHelper.getScannedDocuments(caseDetails);
        List<Document> scannedDocuments = envelope.documents;

        //TODO: compare documents
        return updatedScannedDocuments.equals(scannedDocuments);
    }

    private Boolean compareDocument(Document document, Document updatedDocument) {
        //TODO:
        return document.equals(updatedDocument);
    }
}
