package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
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
    private static final Logger LOG = LoggerFactory.getLogger(CleanupDlqMessagesTest.class);

    @Autowired
    @Qualifier("dlqReceiver")
    private Supplier<IMessageReceiver> dlqReceiverProvider;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Test
    public void testCleanupDlqTask() throws Exception {
        // when
        LOG.info("Send invalid messages to envelopes queue. filename: envelopes/dead-letter-envelope.json");

        // invalid message, which will be sent to dead letter queue
        for (int i = 0; i < 10; i++) {
            envelopeMessager.sendMessageFromFile(
                "envelopes/dead-letter-envelope.json",
                "1234",
                null,
                null
            );
        }

        // then
        await("Dead lettered messages are completed from envelopes dlq.")
            .atMost(4, TimeUnit.MINUTES)
            .pollDelay(120, TimeUnit.SECONDS)
            .pollInterval(15, TimeUnit.SECONDS)
            .until(() -> verifyDlqIsEmpty());
    }

    private boolean verifyDlqIsEmpty() throws ServiceBusException, InterruptedException {
        LOG.info("Reading messages from envelopes Dead letter queue.");

        IMessageReceiver messageReceiver = null;
        IMessage message = null;

        try {
            messageReceiver = dlqReceiverProvider.get();
            message = messageReceiver.receive();
            return message != null;
        } finally {
            if (messageReceiver != null) {
                try {
                    if (message != null) {
                        messageReceiver.abandon(message.getLockToken());
                    }
                    messageReceiver.close();
                } catch (ServiceBusException e) {
                    LOG.error("Error closing dlq connection", e);
                }
            }
        }
    }
}
