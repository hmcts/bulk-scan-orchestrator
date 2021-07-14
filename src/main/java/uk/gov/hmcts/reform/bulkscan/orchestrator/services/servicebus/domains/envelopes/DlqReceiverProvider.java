package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.QueueConfigurationProperties;

import java.util.function.Supplier;

import static uk.gov.hmcts.reform.bulkscan.orchestrator.config.QueueClientsConfig.CONNECTION_STR_FORMAT;

@Component
@Profile("!nosb") // do not register handler for the nosb (test) profile
public class DlqReceiverProvider implements Supplier<ServiceBusReceiverClient> {

    private final String namespace;
    private final QueueConfigurationProperties queueProperties;

    public DlqReceiverProvider(
        @Value("${azure.servicebus.namespace}") String namespace,
        @Qualifier("envelopes-queue-config") QueueConfigurationProperties queueProperties
    ) {
        this.namespace = namespace;
        this.queueProperties = queueProperties;
    }

    @Override
    public ServiceBusReceiverClient get() {
        return new ServiceBusClientBuilder()
            .connectionString(getEnvelopQueueConnectionString(queueProperties))
            .receiver()
            .queueName(queueProperties.getQueueName() + "/$deadletterqueue")
            .buildClient();
    }

    private String getEnvelopQueueConnectionString(QueueConfigurationProperties queuePropertiesy) {
        return String.format(
            CONNECTION_STR_FORMAT,
            namespace,
            queuePropertiesy.getAccessKeyName(),
            queuePropertiesy.getAccessKey()
        );
    }
}
