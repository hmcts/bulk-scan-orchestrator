package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class FunctionalQueueConfig {

    private static final Logger log = LoggerFactory.getLogger(FunctionalQueueConfig.class);

    @Value("${queue.connection-string}")
    private String queueReadConnectionString;

    @Value("${queue.write-connection-string}")
    private String queueWriteConnectionString;

    @Bean
    QueueClient testWriteClient() throws ServiceBusException, InterruptedException {
        log.info("Creating Queue client to send messages with Connection string {}", queueWriteConnectionString);
        return new QueueClient(
            new ConnectionStringBuilder(queueWriteConnectionString),
            ReceiveMode.RECEIVEANDDELETE
        );
    }

    @Bean
    IMessageReceiver testReadClient() throws ServiceBusException, InterruptedException {
        log.info("Creating Message Receiver with Connection string {}", queueReadConnectionString);
        return ClientFactory.createMessageReceiverFromConnectionString(
            queueReadConnectionString,
            ReceiveMode.RECEIVEANDDELETE
        );
    }
}
