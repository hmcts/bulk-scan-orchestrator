package uk.gov.hmcts.reform.bulkscan.orchestrator.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.FunctionalQueueConfig;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentCommand;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
@Import(FunctionalQueueConfig.class)
public class PaymentsMessageSender {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentsMessageSender.class);

    @Autowired
    @Qualifier("payments")
    private QueueClient queueClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void send(PaymentCommand cmd) {
        try {
            final String messageContent = objectMapper.writeValueAsString(cmd);

            IMessage message = new Message(
                UUID.randomUUID().toString(),
                messageContent,
                APPLICATION_JSON.toString()
            );
            message.setLabel(cmd.getLabel());

            queueClient.scheduleMessage(message, Instant.now().plusSeconds(10));

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
}
