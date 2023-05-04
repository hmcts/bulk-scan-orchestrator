package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.EnvelopeMessageProcessor;

@Configuration
@Profile("!nosb") // do not register handler for the nosb (test) profile
@ConditionalOnExpression("!${jms.enabled}")
public class QueueClientsConfig {
    public static final String CONNECTION_STR_FORMAT =
        "Endpoint=sb://%s.servicebus.windows.net;SharedAccessKeyName=%s;SharedAccessKey=%s;";

    @Value("${azure.servicebus.namespace}")
    private String namespace;

    @Bean("envelopes-queue-config")
    @ConfigurationProperties(prefix = "azure.servicebus.envelopes")
    public QueueConfigurationProperties envelopesQueueConfig() {
        return new QueueConfigurationProperties();
    }

    @Bean("envelopes")
    public ServiceBusProcessorClient envelopesMessageReceiver(
        @Qualifier("envelopes-queue-config")QueueConfigurationProperties queueProperties,
        EnvelopeMessageProcessor messageHandler
    ) {
        return new ServiceBusClientBuilder()
            .connectionString(createConnectionString(queueProperties))
            .processor()
            .queueName(queueProperties.getQueueName())
            .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
            .disableAutoComplete()
            .processMessage(messageHandler::processMessage)
            .processError(messageHandler::processException)
            .buildProcessorClient();
    }

    @Bean("processed-envelopes-queue-config")
    @ConfigurationProperties(prefix = "azure.servicebus.processed-envelopes")
    protected QueueConfigurationProperties processedEnvelopesQueueConfig() {
        return new QueueConfigurationProperties();
    }

    @Bean("processed-envelopes")
    public ServiceBusSenderClient processedEnvelopesQueueClient(
        @Qualifier("processed-envelopes-queue-config") QueueConfigurationProperties queueProperties
    ) {
        return createSendClient(queueProperties);
    }

    @Bean("payments-queue-config")
    @ConfigurationProperties(prefix = "azure.servicebus.payments")
    protected QueueConfigurationProperties paymentsQueueConfig() {
        return new QueueConfigurationProperties();
    }

    @Bean("payments")
    public ServiceBusSenderClient paymentsQueueClient(
        @Qualifier("payments-queue-config") QueueConfigurationProperties queueProperties
    ) {
        return createSendClient(queueProperties);
    }

    private ServiceBusSenderClient createSendClient(
        QueueConfigurationProperties queueProperties
    ) {
        return new ServiceBusClientBuilder()
            .connectionString(createConnectionString(queueProperties))
            .sender()
            .queueName(queueProperties.getQueueName())
            .buildClient();

    }

    private String createConnectionString(QueueConfigurationProperties queueProperties) {
        return String.format(
            CONNECTION_STR_FORMAT,
            namespace,
            queueProperties.getAccessKeyName(),
            queueProperties.getAccessKey()
        );
    }
}
