package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@Configuration
@Profile("!nosb") // do not register handler for the nosb (test) profile
public class QueueClientsConfig {

    @Value("${azure.servicebus.namespace}")
    private String namespace;

    @Bean("envelopes-queue-config")
    @ConfigurationProperties(prefix = "azure.servicebus.envelopes")
    public QueueConfigurationProperties envelopesQueueConfig() {
        return new QueueConfigurationProperties();
    }

    @Bean("envelopes")
    public IMessageReceiver envelopesMessageReceiver(
        @Qualifier("envelopes-queue-config") QueueConfigurationProperties queueProperties
    ) throws InterruptedException, ServiceBusException {
        return ClientFactory.createMessageReceiverFromConnectionString(
            getConnectionStringBuilder(queueProperties).toString(),
            ReceiveMode.PEEKLOCK
        );
    }

    @Bean("processed-envelopes-queue-config")
    @ConfigurationProperties(prefix = "azure.servicebus.processed-envelopes")
    protected QueueConfigurationProperties processedEnvelopesQueueConfig() {
        return new QueueConfigurationProperties();
    }

    @Bean("processed-envelopes")
    public QueueClient processedEnvelopesQueueClient(
        @Qualifier("processed-envelopes-queue-config") QueueConfigurationProperties queueProperties
    ) throws InterruptedException, ServiceBusException {
        return new QueueClient(
            getConnectionStringBuilder(queueProperties),
            ReceiveMode.PEEKLOCK
        );
    }

    @Bean("payments-queue-config")
    @ConfigurationProperties(prefix = "azure.servicebus.payments")
    protected QueueConfigurationProperties paymentsQueueConfig() {
        return new QueueConfigurationProperties();
    }

    @Bean("payments")
    public QueueClient paymentsQueueClient(
        @Qualifier("payments-queue-config") QueueConfigurationProperties queueProperties
    ) throws InterruptedException, ServiceBusException {
        var connectionStringBuilder = getConnectionStringBuilder(queueProperties);
        connectionStringBuilder.setOperationTimeout(Duration.ofSeconds(3));

        return new QueueClient(connectionStringBuilder, ReceiveMode.PEEKLOCK);
    }

    private ConnectionStringBuilder getConnectionStringBuilder(QueueConfigurationProperties queueProperties) {
        return new ConnectionStringBuilder(
            namespace,
            queueProperties.getQueueName(),
            queueProperties.getAccessKeyName(),
            queueProperties.getAccessKey()
        );
    }
}
