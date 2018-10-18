package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class FunctionalQueueConfig {

    @Value("${queue.connection-string}")
    private String queueReadConnectionString;

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
    IMessageReceiver testReadClient() throws ServiceBusException, InterruptedException {
        return ClientFactory.createMessageReceiverFromConnectionString(
            queueReadConnectionString,
            ReceiveMode.PEEKLOCK
        );
    }
}
