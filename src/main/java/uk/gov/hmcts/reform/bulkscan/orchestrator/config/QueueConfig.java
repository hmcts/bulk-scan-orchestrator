package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.annotation.PostConstruct;

@Configuration
@Profile("!nosb")
public class QueueConfig {

    private final QueueClient client;
    private final IMessageHandler messageHandler;

    public QueueConfig(QueueClient client, IMessageHandler messageHandler) {
        this.client = client;
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
        client.registerMessageHandler(messageHandler, executorService);
    }

}
