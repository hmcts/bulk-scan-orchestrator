package uk.gov.hmcts.reform.bulkscan.orchestrator;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IMessageOperations;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.IProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

import java.util.UUID;
import java.util.function.Supplier;

public class FunctionalQueueConfig {

    @Value("${queue.envelopes.write-connection-string}")
    private String queueWriteConnectionString;

    @Value("${azure.servicebus.envelopes.connection-string}")
    String connectionString;

    @Value("${azure.servicebus.envelopes.queue-name}")
    String queueName;

    @Bean
    QueueClient testWriteClient() throws ServiceBusException, InterruptedException {
        return new QueueClient(
            new ConnectionStringBuilder(queueWriteConnectionString),
            ReceiveMode.PEEKLOCK
        );
    }

    @Bean(name = "dlqReceiver")
    public Supplier<IMessageReceiver> dlqReceiverProvider() {
        return () -> {
            try {
                return ClientFactory.createMessageReceiverFromConnectionStringBuilder(
                    new ConnectionStringBuilder(connectionString, StringUtils.join(queueName, "/$deadletterqueue")),
                    ReceiveMode.PEEKLOCK
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ServiceBusException e) {
                throw new ConnectionException("Unable to connect to the dlq", e);
            }
            return null;
        };

    }

    @Bean(name = "envelopesReceiver")
    public Supplier<IMessageReceiver> envelopesReceiverProvider() {
        return () -> {
            try {
                return ClientFactory.createMessageReceiverFromConnectionStringBuilder(
                    new ConnectionStringBuilder(connectionString, queueName),
                    ReceiveMode.PEEKLOCK
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ServiceBusException e) {
                throw new ConnectionException("Unable to connect to the envelopes queue", e);
            }
            return null;
        };
    }

    @Bean
    @Profile("nosb") // apply only when Service Bus should not be used
    IMessageOperations testNoServiceBusMessageOperations() {
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

    @Bean
    @Profile("nosb") // apply only when Service Bus should not be used
    IProcessedEnvelopeNotifier testProcessedEnvelopeNotifier() {
        // return implementation that does nothing
        return envelopeId -> { };
    }
}
