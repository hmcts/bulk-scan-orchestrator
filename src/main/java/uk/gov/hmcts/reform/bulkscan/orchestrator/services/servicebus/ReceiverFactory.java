package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.microsoft.azure.servicebus.ClientFactory;
import com.microsoft.azure.servicebus.IMessageReceiver;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

@Component
public class ReceiverFactory implements MessageReceiverFactory {

    private static final Logger logger = LoggerFactory.getLogger(ReceiverFactory.class);

    private final String connString;

    public ReceiverFactory(@Value("${queue.connection-string}") String connString) {
        logger.info("ReceiverFactory - Connection string {}", connString);
        this.connString = connString;
    }

    public IMessageReceiver create() {
        try {
            logger.info("Connecting to the queue - Connection string {}", connString);
            //TODO: change it back to PEEKLOCK
            return ClientFactory.createMessageReceiverFromConnectionString(connString, ReceiveMode.RECEIVEANDDELETE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Unable to connect to queue", e);
        } catch (ServiceBusException e) {
            throw new ConnectionException("Unable to connect to queue", e);
        }
    }

}
