package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
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
    private Supplier<ServiceBusReceiverClient> dlqReceiverProvider;

    @Autowired
    private EnvelopeMessager envelopeMessager;

    @Test
    public void testCleanupDlqTask() throws Exception {
        // when
        // Sending more than 1 invalid message so that we can make sure the dlq messages are completed
        // even when the dlq task acquires lock on some messages
        for (var i = 0; i < 10; i++) {
            envelopeMessager.sendMessageFromFile(
                "envelopes/dead-letter-envelope.json",
                "1234",
                null,
                null,
                null
            );
        }

        // then
        await("Dead lettered messages are completed from envelopes dlq.")
            .forever()
            .until(this::verifyDlqIsEmpty);
    }

    private boolean verifyDlqIsEmpty() throws ServiceBusException {
        LOG.info("Reading messages from envelopes Dead letter queue.");
        try (ServiceBusReceiverClient messageReceiver = dlqReceiverProvider.get()) {
            return messageReceiver.peekMessage() == null;
        }
    }
}
