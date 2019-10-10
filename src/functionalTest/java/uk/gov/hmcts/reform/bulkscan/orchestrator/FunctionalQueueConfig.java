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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.IPaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.IProcessedEnvelopeNotifier;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

import java.util.function.Supplier;

import static org.mockito.Mockito.mock;

public class FunctionalQueueConfig {

    @Value("${queue.envelopes.write-connection-string}")
    private String queueWriteConnectionString;

    @Value("${queue.envelopes.read-connection-string}")
    private String queueReadConnectionString;

    @Bean
    public QueueClient testWriteClient() throws ServiceBusException, InterruptedException {
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
                    new ConnectionStringBuilder(StringUtils.join(queueReadConnectionString, "/$deadletterqueue")),
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
        return envelopeId -> {
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
