package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.MessageHandlerOptions;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import javax.annotation.PostConstruct;

@Configuration
@Profile("!integration")
public class QueueConfig {
    @Autowired
    QueueClient client;
    @Autowired
    IMessageHandler messageHandler;
    @Value("${queue.connections.max:1}")
    private int maxServiceBusConnections;

    @PostConstruct
    public void initialise() throws ServiceBusException, InterruptedException {
        MessageHandlerOptions handlerOptions = new MessageHandlerOptions(
            maxServiceBusConnections,
            true,
            Duration.ofMinutes(1)
        );

        client.registerMessageHandler(messageHandler, handlerOptions);
    }
}
