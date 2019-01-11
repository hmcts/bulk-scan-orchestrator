package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IMessageOperations;

import java.util.UUID;

public class FunctionalQueueConfig {

    @Value("${queue.write-connection-string}")
    private String queueWriteConnectionString;

    @Bean
    QueueClient testWriteClient() throws ServiceBusException, InterruptedException {
        return new QueueClient(
            new ConnectionStringBuilder(queueWriteConnectionString),
            ReceiveMode.PEEKLOCK
        );
    }

    @Bean
    IMessageOperations testMessageOperations() {
        return new IMessageOperations() {
            @Override
            public void complete(UUID lockToken) throws InterruptedException, ServiceBusException {
                // do nothing
            }

            @Override
            public void deadLetter(
                UUID lockToken,
                String reason,
                String description
            ) throws InterruptedException, ServiceBusException {
                // do nothing
            }
        };
    }
}
