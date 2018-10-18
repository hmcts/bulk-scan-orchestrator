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
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import(FunctionalQueueConfig.class)
public class ReadFromTheQueueTest {

    @Autowired
    private IMessageReceiver testReadClient;

    @Autowired
    private QueueClient testWriteClient;

    @Value("${queue.read-interval}")
    private int readInterval;

    @Test
    public void should_read_message_from_the_queue() throws Exception {
        // given
        Message message = new Message();
        message.setMessageId(UUID.randomUUID().toString());
        message.setBody(SampleData.envelopeJson());

        // when
        testWriteClient.send(message);
        Thread.sleep(readInterval + 5_000L); // wait for msg to be processed by the service.

        // then
        assertThat(testReadClient.receive()).isNull();

        // TODO: once implemented, check whether case in CCD has been created, envelope status updated etc...
    }
}
