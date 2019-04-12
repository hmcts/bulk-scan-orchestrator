package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import com.google.common.collect.ImmutableMap;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.servicebus.IMessage;
import org.springframework.stereotype.Component;

@Component
public class AppInsights {

    static final String DEAD_LETTER_EVENT = "DeadLetter";

    private final TelemetryClient telemetryClient;

    public AppInsights(TelemetryClient telemetryClient) {
        this.telemetryClient = telemetryClient;
    }

    public void trackDeadLetteredMessage(IMessage message, String queue, String reason, String description) {
        telemetryClient.trackEvent(
            DEAD_LETTER_EVENT,
            ImmutableMap.of(
                "reason", reason,
                "description", description,
                "messageId", message.getMessageId(),
                "queue", queue
            ),
            ImmutableMap.of(
                "deliveryCount", (double) (message.getDeliveryCount() + 1) // starts from 0
            )
        );
    }
}
