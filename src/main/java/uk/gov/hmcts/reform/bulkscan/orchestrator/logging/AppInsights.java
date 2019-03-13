package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import com.google.common.collect.ImmutableMap;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.servicebus.IMessage;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.logging.appinsights.AbstractAppInsights;

@Component
public class AppInsights extends AbstractAppInsights {

    static final String DEAD_LETTER_EVENT = "DeadLetter";

    public AppInsights(TelemetryClient client) {
        super(client);
    }

    public void trackDeadLetteredMessage(IMessage message, String queue, String reason, String description) {
        telemetry.trackEvent(
            DEAD_LETTER_EVENT,
            ImmutableMap.of(
                "reason", reason,
                "description", description,
                "messageId", message.getMessageId(),
                "queue", queue
            ),
            ImmutableMap.of(
                "deliveryCount", (double) (message.getDeliveryCount() + 1)
            )
        );
    }
}
