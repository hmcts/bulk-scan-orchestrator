package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.QueueConfigurationProperties;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.IProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

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
    public ServiceBusSenderClient testWriteClient(
        QueueConfigurationProperties queueProperties
    ) {
        return new ServiceBusClientBuilder()
            .connectionString(
                String.format(
                    CONNECTION_STR_FORMAT,
                    queueNamespace,
                    queueWriteAccessKeyName,
                    queueWriteAccessKey
                )
            )
            .sender()
            .queueName(queueProperties.getQueueName())
            .buildClient();

    }


    @Bean(name = "dlqReceiver")
    public Supplier<IMessageReceiver> dlqReceiverProvider() {
        return () -> {
            try {
                return ClientFactory.createMessageReceiverFromConnectionStringBuilder(
                    new ConnectionStringBuilder(
                        queueNamespace,
                        StringUtils.join(queueName, "/$deadletterqueue"),
                        queueReadAccessKeyName,
                        queueReadAccessKey
                    ),
                    ReceiveMode.PEEKLOCK
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConnectionException("Unable to connect to the dlq", e);
            } catch (ServiceBusException e) {
                throw new ConnectionException("Unable to connect to the dlq", e);
            }
        };

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

    @Bean
    @Profile("nosb") // apply only when Service Bus should not be used
    public IMessageReceiver testMessageReceiver() {
        return mock(IMessageReceiver.class);
    }
}
