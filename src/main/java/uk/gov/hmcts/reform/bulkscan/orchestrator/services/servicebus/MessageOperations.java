package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.MINUTES;

@Service
@Profile("!nosb") // do not register for the nosb (test) profile
public class MessageOperations implements IMessageOperations {

    private QueueClient queueClient;

    @Autowired
    public MessageOperations(@Qualifier("envelopes") QueueClient queueClient) {
        this.queueClient = queueClient;
    }

    public void complete(UUID lockToken) throws InterruptedException, ServiceBusException {
        queueClient.complete(lockToken);
    }

    public void deadLetter(
        UUID lockToken,
        String reason,
        String description
    ) throws InterruptedException, ServiceBusException {
        queueClient.deadLetter(
            lockToken,
            reason,
            description,
            ImmutableMap.of("TimeToLive", Duration.of(5, MINUTES))
        );
    }
}
