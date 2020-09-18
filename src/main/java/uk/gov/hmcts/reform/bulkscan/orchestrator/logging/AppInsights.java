package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import com.google.common.collect.ImmutableMap;
import com.microsoft.applicationinsights.TelemetryClient;
import org.apache.qpid.jms.message.JmsMessage;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.Message;

@Component
public class AppInsights {

    static final String DEAD_LETTER_EVENT = "DeadLetter";

    private final TelemetryClient telemetryClient;

    public AppInsights(TelemetryClient telemetryClient) {
        this.telemetryClient = telemetryClient;
    }

    public void trackDeadLetteredMessage(Message message, String queue, String reason, String description) throws JMSException {
        JmsMessage jmsMessage = (JmsMessage) message;
        telemetryClient.trackEvent(
            DEAD_LETTER_EVENT,
            ImmutableMap.of(
                "reason", reason,
                "description", description,
                "messageId", message.getJMSMessageID(),
                "queue", queue
            ),
            ImmutableMap.of(
                "deliveryCount", (double) (jmsMessage.getFacade().getRedeliveryCount() + 1) // starts from 0
            )
        );
    }
}
