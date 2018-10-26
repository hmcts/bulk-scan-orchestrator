package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
@ConditionalOnExpression("#{!environment.getProperty('spring.profiles.active').contains('integration') && !environment.getProperty('spring.profiles.active').contains('functional')}")
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
