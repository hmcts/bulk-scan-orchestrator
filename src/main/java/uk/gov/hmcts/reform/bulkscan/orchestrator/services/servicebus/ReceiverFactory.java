package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

@Component
public class ReceiverFactory implements MessageReceiverFactory {

    private final String connString;

    public ReceiverFactory(@Value("${queue.connection-string}") String connString) {
        this.connString = connString;
    }

    public IMessageReceiver create() {
        try {
            return ClientFactory.createMessageReceiverFromConnectionString(connString, ReceiveMode.PEEKLOCK);
        } catch (InterruptedException | ServiceBusException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Unable to connect to queue", e);
        }
    }
}
