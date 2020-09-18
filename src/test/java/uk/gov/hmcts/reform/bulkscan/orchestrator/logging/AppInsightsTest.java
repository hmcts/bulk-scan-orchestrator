package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import com.google.common.collect.ImmutableMap;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.servicebus.IMessage;
import org.apache.qpid.jms.message.JmsMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jms.JMSException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppInsightsTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JmsMessage message;

    @Mock
    private TelemetryClient telemetryClient;

    private AppInsights appInsights;

    @BeforeEach
    void setUp() {
        appInsights = new AppInsights(telemetryClient);
    }

    @Test
    void should_record_dead_letter_event() throws JMSException {
        String messageId = "message id";
        int deliveryCount = 5;
        String queue = "queue name";
        String reason = "some reason";
        String description = "some description";

        when(message.getJMSMessageID()).thenReturn(messageId);
        when(message.getFacade().getRedeliveryCount()).thenReturn(deliveryCount);

        appInsights.trackDeadLetteredMessage(message, queue, reason, description);

        verify(telemetryClient).trackEvent(
            AppInsights.DEAD_LETTER_EVENT,
            ImmutableMap.of(
                "reason", reason,
                "description", description,
                "messageId", messageId,
                "queue", queue
            ),
            ImmutableMap.of(
                "deliveryCount", (double) (deliveryCount + 1)
            )
        );
    }
}
