package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

import java.util.function.Supplier;

@Component
@Profile("!nosb") // do not register handler for the nosb (test) profile
public class DlqReceiverProvider implements Supplier<IMessageReceiver> {

    private final String connectionString;
    private final String queueName;

    public DlqReceiverProvider(
        @Value("${azure.servicebus.envelopes.connection-string}") String connectionString,
        @Value("${azure.servicebus.envelopes.queue-name}") String queueName
    ) {
        this.connectionString = connectionString;
        this.queueName = queueName;
    }

    @Override
    public IMessageReceiver get() {
        try {
            return ClientFactory.createMessageReceiverFromConnectionStringBuilder(
                new ConnectionStringBuilder(connectionString, StringUtils.join(queueName, "/$deadletterqueue")),
                ReceiveMode.PEEKLOCK
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Unable to connect to the dlq", e);
        } catch (ServiceBusException e) {
            throw new ConnectionException("Unable to connect to the dlq", e);
        }
    }
}
