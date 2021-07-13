package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.models.SubQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.IProcessedEnvelopeNotifier;

import java.util.function.Supplier;

import static org.mockito.Mockito.mock;

public class FunctionalQueueConfig {
    public static final String CONNECTION_STR_FORMAT =
        "Endpoint=sb://%s.servicebus.windows.net;SharedAccessKeyName=%s;SharedAccessKey=%s;";

    @Value("${queue.envelopes.name}")
    private String queueName;

    @Value("${queue.envelopes.write-access-key}")
    private String queueWriteAccessKey;

    @Value("${queue.envelopes.read-access-key}")
    private String queueReadAccessKey;

    @Value("${queue.read-access-key-name}")
    private String queueReadAccessKeyName;

    @Value("${queue.write-access-key-name}")
    private String queueWriteAccessKeyName;

    @Value("${queue.namespace}")
    private String queueNamespace;

    @Bean
    public ServiceBusSenderClient testWriteClient() {
        return new ServiceBusClientBuilder()
            .connectionString(getEnvelopQueueConnectionString(queueWriteAccessKeyName, queueWriteAccessKey))
            .sender()
            .queueName(queueName)
            .buildClient();
    }

    @Bean(name = "dlqReceiver")
    public Supplier<ServiceBusReceiverClient> dlqReceiverProvider() {
        return () ->
            new ServiceBusClientBuilder()
                .connectionString(getEnvelopQueueConnectionString(queueReadAccessKeyName, queueReadAccessKey))
                .receiver()
                .queueName(queueName)
                .subQueue(SubQueue.DEAD_LETTER_QUEUE)
                .buildClient();
    }

    @Bean
    @Profile("nosb") // apply only when Service Bus should not be used
    IProcessedEnvelopeNotifier testProcessedEnvelopeNotifier() {
        // return implementation that does nothing
        return (envelopeId, ccdId, ccdAction) -> {
        };
    }

    @Bean
    @Profile("nosb") // apply only when Service Bus should not be used
    IPaymentsPublisher testPaymentsPublisher() {
        // return implementation that does nothing
        return cmd -> {
        };
    }

    private String getEnvelopQueueConnectionString(String accessKeyName, String accessKey) {
        return String.format(
            CONNECTION_STR_FORMAT,
            queueNamespace,
            accessKeyName,
            accessKey
        );
    }

    @Bean("envelopes-dead-letter-send")
    public ServiceBusSenderClient envelopesDeadLetterSend() {
        return mock(ServiceBusSenderClient.class);
    }

    @Bean("envelopes")
    public ServiceBusProcessorClient envelopesMessageReceiver() {
        return mock(ServiceBusProcessorClient.class);
    }

}
