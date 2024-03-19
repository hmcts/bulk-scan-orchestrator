package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AppInsights {

    static final String DEAD_LETTER_EVENT = "DeadLetter";

    private final TelemetryClient telemetryClient = new TelemetryClient();

    public void trackDeadLetteredMessage(
        ServiceBusReceivedMessage message,
        String queue,
        String reason,
        String description
    ) {
        telemetryClient.trackEvent(
            DEAD_LETTER_EVENT,
            Map.of(
                "reason", reason,
                "description", description,
                "messageId", message.getMessageId(),
                "queue", queue
            ),
            Map.of(
                "deliveryCount", (double) (message.getDeliveryCount() + 1) // starts from 0
            )
        );
    }
}
