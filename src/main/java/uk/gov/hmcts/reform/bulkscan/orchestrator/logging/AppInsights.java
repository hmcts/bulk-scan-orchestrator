package uk.gov.hmcts.reform.bulkscan.orchestrator.logging;

import com.google.common.collect.ImmutableMap;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.Duration;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.web.internal.RequestTelemetryContext;
import com.microsoft.applicationinsights.web.internal.ThreadContext;
import com.microsoft.applicationinsights.web.internal.correlation.TelemetryCorrelationUtils;
import com.microsoft.azure.servicebus.IMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AppInsights {

    static final String DEAD_LETTER_EVENT = "DeadLetter";

    static final String SERVICE_BUS_DEPENDENCY_TYPE = "ServiceBus";

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

    // dependencies

    public void trackServiceBusMessage(String dependencyName, String commandName, Instant start, boolean success) {
        // dependency definition
        RemoteDependencyTelemetry dependencyTelemetry = new RemoteDependencyTelemetry(
            dependencyName,
            commandName,
            new Duration(ChronoUnit.MILLIS.between(start, Instant.now())),
            success
        );
        dependencyTelemetry.setType(SERVICE_BUS_DEPENDENCY_TYPE);

        // tracing support
        RequestTelemetryContext context = ThreadContext.getRequestTelemetryContext();

        if (context != null) {
            RequestTelemetry requestTelemetry = context.getHttpRequestTelemetry();
            dependencyTelemetry.setId(TelemetryCorrelationUtils.generateChildDependencyId());
            dependencyTelemetry.getContext().getOperation().setId(
                requestTelemetry.getContext().getOperation().getId()
            );
            dependencyTelemetry.getContext().getOperation().setParentId(
                requestTelemetry.getId()
            );
        }

        telemetryClient.trackDependency(dependencyTelemetry);
    }
}
