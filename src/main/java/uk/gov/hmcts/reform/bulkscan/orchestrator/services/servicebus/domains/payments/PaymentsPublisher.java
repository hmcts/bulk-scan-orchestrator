package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentCommand;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
@Profile("!nosb") // do not register for the nosb (test) profile
@ConditionalOnExpression("!${jms.enabled}")
public class PaymentsPublisher implements IPaymentsPublisher {

    // TODO: make version of this

    private static final Logger LOG = LoggerFactory.getLogger(PaymentsPublisher.class);

    private final ServiceBusSenderClient queueClient;
    private final ObjectMapper objectMapper;
    private final int retryCount;
    private final int retryWait;

    public PaymentsPublisher(
        @Qualifier("payments") ServiceBusSenderClient queueClient,
        ObjectMapper objectMapper,
        @Value("${azure.servicebus.payments.manual-retry-count}") int retryCount,
        @Value("${azure.servicebus.payments.manual-retry-wait-in-ms}") int retryWait
    ) {
        this.queueClient = queueClient;
        this.objectMapper = objectMapper;
        this.retryCount = retryCount;
        this.retryWait = retryWait;
    }

    @Override
    public void send(PaymentCommand cmd) {
        try {
            final String messageContent = objectMapper.writeValueAsString(cmd);

            ServiceBusMessage message = new ServiceBusMessage(messageContent);
            message.setContentType(APPLICATION_JSON.toString());
            message.setMessageId(UUID.randomUUID().toString());

            message.setSubject(cmd.getLabel());

            LOG.info("About to send message to payments queue. ID: {}, Label: {}, Content: {}",
                    message.getMessageId(),
                    message.getSubject(),
                    messageContent
            );

            doSend(message, retryCount);

            LOG.info(
                "Sent message to payments queue. ID: {}, Label: {}, Content: {}",
                message.getMessageId(),
                message.getSubject(),
                messageContent
            );
        } catch (Exception ex) {
            throw new PaymentsPublishingException(
                "An error occurred when trying to publish message to payments queue.",
                ex
            );
        }
    }

    private void doSend(ServiceBusMessage message, int retryCount) throws InterruptedException {
        try {
            queueClient.sendMessage(message);
        } catch (Exception ex) {
            if (retryCount > 0) {
                LOG.error(
                        "Sent message to payments queue got error, "
                                + "Message ID: {}. Remaining Retry Count: {}, Retrying...",
                        message.getMessageId(),
                        retryCount,
                        ex
                );
                Thread.sleep(retryWait);
                doSend(message, --retryCount);
            } else {
                throw ex;
            }

        }
    }
}
