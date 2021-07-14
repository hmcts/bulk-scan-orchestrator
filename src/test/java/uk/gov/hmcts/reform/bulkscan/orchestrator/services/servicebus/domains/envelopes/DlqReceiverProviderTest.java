package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.bulkscan.orchestrator.config.QueueConfigurationProperties;

class DlqReceiverProviderTest {

    private static final String NAMESPACE = "r.com";

    @Test
    void should_fail_to_connect_to_dlq() {
        // given
        var queueProperties = getQueueProperties();
        var provider = new DlqReceiverProvider(NAMESPACE, queueProperties);
        // when
        Assertions
            .assertThat(provider.get())
            .isNotNull();
    }

    private QueueConfigurationProperties getQueueProperties() {
        var queueProperties = new QueueConfigurationProperties();

        queueProperties.setAccessKey("LET ME IN");
        queueProperties.setAccessKeyName("ROOT");
        queueProperties.setQueueName("TEST");

        return queueProperties;
    }
}
