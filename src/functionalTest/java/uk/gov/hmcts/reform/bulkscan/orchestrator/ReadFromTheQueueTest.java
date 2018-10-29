package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import(FunctionalQueueConfig.class)
@ActiveProfiles("nosb")  // no servicebus queue handler registration
public class ReadFromTheQueueTest {

    @Autowired
    private IMessageReceiver testReadClient;

    @Autowired
    private QueueClient testWriteClient;

    @Value("${queue.read-interval}")
    private int readInterval;

    @Test
    public void should_consume_message_from_the_queue() throws Exception {
        // given
        Message message = new Message();
        message.setMessageId(UUID.randomUUID().toString());
        message.setBody(SampleData.envelopeJson());

        // when
        testWriteClient.send(message);

        // then
        // wait for msg to be processed (and removed from the queue) by the service
        await()
            .atMost(readInterval + 5_000L, TimeUnit.MILLISECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .until(() -> testReadClient.peek() == null);
    }
}
