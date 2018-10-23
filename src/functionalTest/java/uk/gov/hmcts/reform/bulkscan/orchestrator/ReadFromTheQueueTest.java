package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class ReadFromTheQueueTest {

    private IMessageReceiver readClient;
    private QueueClient writeClient;
    private int readInterval;

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
    public void should_consume_message_from_the_queue() throws Exception {
        // given
        Message message = new Message();
        message.setMessageId(UUID.randomUUID().toString());
        message.setBody(SampleData.envelopeJson());

        // when
        writeClient.send(message);

        // then

        // wait for msg to be processed (and removed from the queue) by the service
        await()
            .atMost(readInterval + 5_000L, TimeUnit.MILLISECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .until(() -> readClient.peek() == null);
    }
}
