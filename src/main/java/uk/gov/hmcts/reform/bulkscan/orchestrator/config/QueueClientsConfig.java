package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!nosb") // do not register handler for the nosb (test) profile
public class QueueClientsConfig {

    @Bean("envelopes")
    public QueueClient envelopesQueueClient(
        @Value("${azure.servicebus.envelopes.connection-string}") String connectionString,
        @Value("${azure.servicebus.envelopes.queue-name}") String queueName
    ) throws InterruptedException, ServiceBusException {
        return new QueueClient(
            new ConnectionStringBuilder(connectionString, queueName),
            ReceiveMode.PEEKLOCK
        );
    }
}
