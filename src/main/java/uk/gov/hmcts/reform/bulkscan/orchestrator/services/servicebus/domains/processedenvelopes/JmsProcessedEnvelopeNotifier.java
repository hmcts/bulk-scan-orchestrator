package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.stereotype.Service;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.UUID;

/**
 * Notifies about successfully processed envelopes via queue.
 */
@Service
@Profile("!nosb") // do not register for the nosb (test) profile
@ConditionalOnExpression("${jms.enabled}")
public class JmsProcessedEnvelopeNotifier implements IProcessedEnvelopeNotifier {

    private final Logger log = LoggerFactory.getLogger(JmsProcessedEnvelopeNotifier.class);

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    public JmsProcessedEnvelopeNotifier(
        JmsTemplate jmsTemplate,
        ObjectMapper objectMapper
    ) {
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = objectMapper;
    }

    public void notify(String envelopeId, Long ccdId, EnvelopeCcdAction envelopeCcdAction) {
        try {
            String messageBody =
                objectMapper.writeValueAsString(new ProcessedEnvelope(envelopeId, ccdId, envelopeCcdAction));
            String messageId = UUID.randomUUID().toString();

            log.info("About to send message to processed-envelopes queue. ID: {}, Content: {}",
                messageId,
                messageBody
            );

            jmsTemplate.convertAndSend("processed-envelopes", messageBody, new MessagePostProcessor() {
                @Override
                public Message postProcessMessage(Message message) throws JMSException {
                    message.setJMSMessageID(messageId);
                    return message;
                }
            });

            log.info("Sent message to processed-envelopes queue. Message Body: {}", messageBody);
        } catch (Exception ex) {
            throw new NotificationSendingException(
                "An error occurred when trying to send notification about successfully processed envelope",
                ex
            );
        }
    }
}
