package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.CcdCaseCreator;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.CaseRetriever;

@RunWith(SpringRunner.class)
@SpringBootTest
@ConfigurationProperties
public abstract class BaseTest {

    IMessageReceiver readClient;
    QueueClient writeClient;

    @Value("queue.read-interval")
    public int readInterval;

    @Value("queue.connection-string")
    public String queueReadConnectionString;

    @Value("queue.write-connection-string")
    public String queueWriteConnectionString;

    @Autowired
    CaseRetriever caseRetriever;

    @Autowired
    CcdCaseCreator ccdCaseCreator;

    @Before
    public void setUp() throws ServiceBusException, InterruptedException {
        this.readClient = ClientFactory.createMessageReceiverFromConnectionString(
            queueReadConnectionString,
            ReceiveMode.PEEKLOCK
        );

        this.writeClient = new QueueClient(
            new ConnectionStringBuilder(queueWriteConnectionString),
            ReceiveMode.PEEKLOCK
        );

    }

}
