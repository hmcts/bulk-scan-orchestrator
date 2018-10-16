package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.Message;
import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ReadFromTheQueueTest extends BaseTest {

    @Test
    public void should_read_message_from_the_queue() throws Exception {
        // given
        Message message = new Message();
        message.setMessageId(UUID.randomUUID().toString());
        message.setBody(SampleData.envelopeJson());

        // when
        writeClient.send(message);
        Thread.sleep(readInterval + 5_000L); // wait for msg to be processed by the service.

        // then
        assertThat(readClient.receive()).isNull();

        // TODO: once implemented, check whether case in CCD has been created, envelope status updated etc...
    }
}
