package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;

@RunWith(SpringRunner.class)
@SpringBootTest
public abstract class BaseTest {

    IMessageReceiver readClient;
    QueueClient writeClient;
    int readInterval;

    @Autowired
    CaseRetriever caseRetriever;

    @Autowired
    CcdCaseCreator ccdCaseCreator;

    @Before
    public void setUp() throws ServiceBusException, InterruptedException {
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

}
