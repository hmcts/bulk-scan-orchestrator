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
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentCommand;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
@Profile("!nosb") // do not register for the nosb (test) profile
@ConditionalOnExpression("${jms.enabled}")
public class JmsPaymentsPublisher implements IPaymentsPublisher {

    // TODO: make version of this

    private static final Logger LOG = LoggerFactory.getLogger(JmsPaymentsPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    public JmsPaymentsPublisher(
        JmsTemplate jmsTemplate,
        ObjectMapper objectMapper
    ) {
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(PaymentCommand cmd) {
        try {

            String messageContent = objectMapper.writeValueAsString(cmd);
            String messageId = UUID.randomUUID().toString();

            LOG.info("About to send message to payments queue. ID: {}, Content: {}",
                    messageId,
                    messageContent
            );

            jmsTemplate.convertAndSend("payments", messageContent, new MessagePostProcessor() {
                @Override
                public Message postProcessMessage(Message message) throws JMSException {
                    message.setJMSMessageID(messageId);
                    return message;
                }
            });

            LOG.info(
                "Sent message to payments queue. ID: {}, Content: {}",
                messageId,
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
