package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import com.google.common.collect.ImmutableMap;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.TelemetryContext;
import com.microsoft.azure.servicebus.IMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppInsightsTest {

    @Mock
    private IMessage message;

    @Mock
    private TelemetryClient telemetryClient;

    private AppInsights appInsights;

    @BeforeEach
    void setUp() {
        TelemetryContext telemetryContext = new TelemetryContext();
        telemetryContext.setInstrumentationKey("test");
        when(telemetryClient.getContext()).thenReturn(telemetryContext);

        appInsights = new AppInsights(telemetryClient);
    }

    @Test
    void should_record_dead_letter_event() {
        String messageId = "message id";
        long deliveryCount = 5;
        String queue = "queue name";
        String reason = "some reason";
        String description = "some description";

        when(message.getMessageId()).thenReturn(messageId);
        when(message.getDeliveryCount()).thenReturn(deliveryCount);

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
