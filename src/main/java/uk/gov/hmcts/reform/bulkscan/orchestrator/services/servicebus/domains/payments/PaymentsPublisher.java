package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentCommand;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
@Profile("!nosb") // do not register for the nosb (test) profile
public class PaymentsPublisher implements IPaymentsPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentsPublisher.class);

    private final QueueClient queueClient;
    private final ObjectMapper objectMapper;

    private final int retryCount;

    public PaymentsPublisher(
        @Qualifier("payments") QueueClient queueClient,
        ObjectMapper objectMapper,
        @Value("azure.servicebus.payments.manual-retry-count") int retryCount
    ) {
        this.queueClient = queueClient;
        this.objectMapper = objectMapper;
        this.retryCount = retryCount;
    }

    @Override
    public void send(PaymentCommand cmd) {
        try {
            final String messageContent = objectMapper.writeValueAsString(cmd);

            IMessage message = new Message(
                UUID.randomUUID().toString(),
                messageContent,
                APPLICATION_JSON.toString()
            );
            message.setLabel(cmd.getLabel());

            LOG.info("About to send message to payments queue. ID: {}, Label: {}, Content: {}",
                    message.getMessageId(),
                    message.getLabel(),
                    messageContent
            );

            doSend(message, retryCount);

            LOG.info(
                "Sent message to payments queue. ID: {}, Label: {}, Content: {}",
                message.getMessageId(),
                message.getLabel(),
                messageContent
            );
        } catch (Exception ex) {
            throw new PaymentsPublishingException(
                "An error occurred when trying to publish message to payments queue.",
                ex
            );
        }
    }

    private void doSend(IMessage message, int retryCount) throws ServiceBusException, InterruptedException {
        try {
            queueClient.send(message);
            LOG.info("Sent message to payments queue. ID: {}, Label: {}",
                    message.getMessageId(),
                    message.getLabel());
        } catch (Exception ex) {
            if (retryCount > 0) {
                LOG.error(
                        "Sent message to payments queue got error, "
                                + "Message ID: {}. Remaining Retry Count: {}, Retrying...",
                        message.getMessageId(),
                        retryCount,
                        ex
                );
                doSend(message, --retryCount);
            } else {
                throw ex;
            }

        }
    }
}
