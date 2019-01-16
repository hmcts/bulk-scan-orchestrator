package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.MessageHandlerOptions;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.MessagingEntityNotFoundException;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;

/**
 * Registers handler with Azure Service bus to read messages when
 * they are available in the queue.
 */
@Configuration
@Profile("!nosb") // do not register handler for the nosb (test) profile
public class QueueConfig {

    private static final Logger log = LoggerFactory.getLogger(QueueConfig.class);

    private final QueueClient envelopesQueueClient;
    private final IMessageHandler messageHandler;

    public QueueConfig(
        @Qualifier("envelopes") QueueClient envelopesQueueClient,
        IMessageHandler messageHandler
    ) {
        this.envelopesQueueClient = envelopesQueueClient;
        this.messageHandler = messageHandler;
    }

    @PostConstruct
    public void initialise() throws ServiceBusException, InterruptedException {
        // Note: single threaded queue reader for now to keep it simple.
        ThreadFactory namedThreadFactory =
            new ThreadFactoryBuilder().setNameFormat("queue-reader-%d").build();
        ExecutorService executorService = Executors.newSingleThreadExecutor(
            namedThreadFactory
        );

        // Note: retry init otherwise AKS setup fails as a queue is
        // created in that environment only after deployment is complete.
        int tries = 0;
        while (true) {
            try {
                envelopesQueueClient.registerMessageHandler(
                    messageHandler,
                    new MessageHandlerOptions(1, false, Duration.ofMinutes(5)),
                    executorService
                );

                return;
            } catch (UnsupportedOperationException e) {
                log.info("Register handler error: {}.", e.getMessage());
                // trying to register again, ignore
                return;
            } catch (MessagingEntityNotFoundException e) {
                tries++;
                if (tries >= 5) {
                    throw e;
                }
                log.info("Register handler error: {}. Retrying...", e.getMessage());
                Uninterruptibles.sleepUninterruptibly(10L * tries, TimeUnit.SECONDS);
            }
        }
    }

}
