package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import com.google.common.collect.ImmutableMap;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;
import com.microsoft.applicationinsights.web.internal.RequestTelemetryContext;
import com.microsoft.applicationinsights.web.internal.ThreadContext;
import com.microsoft.azure.servicebus.IMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.logging.DependencyCommand.QUEUE_MESSAGE_RECEIVED;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.logging.DependencyName.ENVELOPE_QUEUE;

@ExtendWith(MockitoExtension.class)
class AppInsightsTest {

    @Mock
    private IMessage message;

    @Mock
    private TelemetryClient telemetryClient;

    private AppInsights appInsights;

    @BeforeAll
    static void setUpRequestContext() {
        ThreadContext.setRequestTelemetryContext(
            new RequestTelemetryContext(now().toEpochMilli(), null)
        );
    }

    @BeforeEach
    void setUp() {
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

    @Test
    void should_record_service_bus_message_activity() {
        // given
        Instant finished = now();
        ArgumentCaptor<RemoteDependencyTelemetry> dependencyCaptor =
            ArgumentCaptor.forClass(RemoteDependencyTelemetry.class);

        // when
        appInsights.trackServiceBusMessage(ENVELOPE_QUEUE, QUEUE_MESSAGE_RECEIVED, finished, true);
        appInsights.trackServiceBusMessage(ENVELOPE_QUEUE, QUEUE_MESSAGE_RECEIVED, finished, false);

        // then
        verify(telemetryClient, times(2)).trackDependency(dependencyCaptor.capture());
        assertThat(dependencyCaptor.getAllValues())
            .extracting(dependency ->
                tuple(dependency.getType(), dependency.getName(), dependency.getCommandName(), dependency.getSuccess())
            )
            .containsExactlyInAnyOrder(
                tuple(AppInsights.SERVICE_BUS_DEPENDENCY_TYPE, ENVELOPE_QUEUE, QUEUE_MESSAGE_RECEIVED, true),
                tuple(AppInsights.SERVICE_BUS_DEPENDENCY_TYPE, ENVELOPE_QUEUE, QUEUE_MESSAGE_RECEIVED, false)
            );
    }
}
