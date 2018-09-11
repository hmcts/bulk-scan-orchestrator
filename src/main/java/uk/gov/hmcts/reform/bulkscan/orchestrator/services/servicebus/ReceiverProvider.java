package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

public class ReceiverProvider {

    private final String connString;

    public ReceiverProvider(String connString) {
        this.connString = connString;
    }

    public IMessageReceiver get() {
        try {
            return ClientFactory.createMessageReceiverFromConnectionString(connString, ReceiveMode.PEEKLOCK);
        } catch (InterruptedException | ServiceBusException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Unable to connect to queue", e);
        }
    }
}
