package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes;

import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.QueueConfigurationProperties;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.exceptions.ConnectionException;

class DlqReceiverProviderTest {

    private static final String NAMESPACE = "==namespace==";

    @Test
    void should_fail_to_connect_to_dlq() {
        // given
        var queueProperties = getQueueProperties();
        var provider = new DlqReceiverProvider(NAMESPACE, queueProperties);

        // when
        Assertions.assertThatCode(provider::get)
            // then
            .isInstanceOf(ConnectionException.class)
            .hasMessage("Unable to connect to the dlq")
            .hasCauseInstanceOf(ServiceBusException.class);
    }

    private QueueConfigurationProperties getQueueProperties() {
        var queueProperties = new QueueConfigurationProperties();

        queueProperties.setAccessKey("LET ME IN");
        queueProperties.setAccessKeyName("ROOT");
        queueProperties.setQueueName("TEST");

        return queueProperties;
    }
}
