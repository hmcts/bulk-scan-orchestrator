package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.annotation.PostConstruct;

@Configuration
@Profile("!integration")
public class QueueConfig {

    private final QueueClient client;
    private final IMessageHandler messageHandler;

    public QueueConfig(QueueClient client, IMessageHandler messageHandler) {
        this.client = client;
        this.messageHandler = messageHandler;
    }

    @PostConstruct
    public void initialise() throws ServiceBusException, InterruptedException {
        client.registerMessageHandler(messageHandler);
    }

}
