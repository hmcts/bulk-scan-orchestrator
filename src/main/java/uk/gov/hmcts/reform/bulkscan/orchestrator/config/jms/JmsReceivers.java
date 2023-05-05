package uk.gov.hmcts.reform.bulkscan.orchestrator.config.jms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.JmsListener;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.JmsEnvelopeMessageProcessor;

import javax.jms.JMSException;
import javax.jms.Message;

@Configuration()
@ConditionalOnProperty(name = "jms.enabled", havingValue = "true")
public class JmsReceivers {

    private static final Logger log = LoggerFactory.getLogger(JmsReceivers.class);

    private final JmsEnvelopeMessageProcessor jmsEnvelopeMessageProcessor;

    public JmsReceivers(
        JmsEnvelopeMessageProcessor jmsEnvelopeMessageProcessor
    ) {
        this.jmsEnvelopeMessageProcessor = jmsEnvelopeMessageProcessor;
    }

    // TODO
    // envelopes
    // processed-envelopes
    // payments

    @JmsListener(destination = "envelopes", containerFactory = "orchestratorEventQueueContainerFactory")
    public void receiveMessage(Message message) throws JMSException {
        String messageBody = ((javax.jms.TextMessage) message).getText();
        log.info("Received Message {} on Service Bus. Delivery count is: {}",
                 messageBody, message.getStringProperty("JMSXDeliveryCount"));
        jmsEnvelopeMessageProcessor.processMessage(message, messageBody);
        log.info("Message finished/completed");
    }
}
