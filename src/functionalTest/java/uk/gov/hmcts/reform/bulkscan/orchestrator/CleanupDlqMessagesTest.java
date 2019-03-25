package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.awaitility.Duration;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.helper.EnvelopeMessager;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("nosb")  // no servicebus queue handler registration
@Import(FunctionalQueueConfig.class)
public class CleanupDlqMessagesTest {
    private static final Logger log = LoggerFactory.getLogger(CleanupDlqMessagesTest.class);

    @Autowired
    @Qualifier("dlqReceiver")
    Supplier<IMessageReceiver> dlqReceiverProvider;

    @Autowired
    @Qualifier("envelopesReceiver")
    Supplier<IMessageReceiver> envelopesReceiverProvider;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Test
    public void testCleanupDlqTask() throws InterruptedException, ServiceBusException, JSONException {
        // given
        log.info("Send a message to envelopes queue. filename: envelopes/dead-letter-envelope.json");
        envelopeMessager.sendMessageFromFile(
            "envelopes/dead-letter-envelope.json",
            "1234",
            null,
            null
        );

        // when
        log.info("Dead letter the first message on envelope queue.");
        deadLetterMessage();

        await("Message exists in envelopes dead letter queue")
            .atMost(30, TimeUnit.SECONDS)
            .pollInterval(Duration.ONE_SECOND)
            .until(this::verifyDlqMessagesExists);

        // then
        await("Message deleted from envelopes dead letter queue")
            .atMost(120, TimeUnit.SECONDS)
            .pollInterval(Duration.FIVE_SECONDS)
            .until(() -> !verifyDlqMessagesExists());
    }

    private void deadLetterMessage() throws ServiceBusException, InterruptedException {
        IMessageReceiver envelopesReceiver = envelopesReceiverProvider.get();

        try {
            IMessage message = envelopesReceiver.receive();
            while (message != null) {
                log.info("Dead lettering message with Id: {}", message.getMessageId());
                envelopesReceiver.deadLetter(message.getLockToken());
            }
        } finally {
            try {
                envelopesReceiver.close();
            } catch (ServiceBusException e) {
                log.error("Error closing dlq connection", e);
            }
        }
    }

    private boolean verifyDlqMessagesExists() throws ServiceBusException, InterruptedException {
        log.info("Reading messages from envelopes Dead letter queue.");
        IMessageReceiver messageReceiver = null;

        try {
            messageReceiver = dlqReceiverProvider.get();

            IMessage message = messageReceiver.receive();
            while (message != null) {
                return true;
            }
        } finally {
            if (messageReceiver != null) {
                try {
                    messageReceiver.close();
                } catch (ServiceBusException e) {
                    log.error("Error closing dlq connection", e);
                }
            }
        }
        return false;
    }
}
