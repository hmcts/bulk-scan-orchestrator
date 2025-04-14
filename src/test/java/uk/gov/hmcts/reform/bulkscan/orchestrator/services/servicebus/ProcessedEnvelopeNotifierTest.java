package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.azure.messaging.servicebus.ServiceBusErrorSource;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import uk.gov.hmcts.reform.bulkscan.orchestrator.errorhandling.exceptions.NotificationSendingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.ProcessedEnvelopeNotifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.AUTO_ATTACHED_TO_CASE;
import static uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes.EnvelopeCcdAction.EXCEPTION_RECORD;

@ExtendWith(MockitoExtension.class)
class ProcessedEnvelopeNotifierTest {

    private ProcessedEnvelopeNotifier notifier;

    @Mock
    private ServiceBusSenderClient queueClient;

    @BeforeEach
    void setUp() {
        notifier = new ProcessedEnvelopeNotifier(queueClient, new ObjectMapper());
    }

    @Test
    void notify_should_send_message_with_right_content() throws Exception {
        // given
        String envelopeId = UUID.randomUUID().toString();
        Long ccdId = 4342349506L;
        EnvelopeCcdAction envelopeCcdAction = AUTO_ATTACHED_TO_CASE;
        // when
        notifier.notify(envelopeId, ccdId, envelopeCcdAction);

        // then
        ArgumentCaptor<ServiceBusMessage> messageCaptor = ArgumentCaptor.forClass(ServiceBusMessage.class);
        verify(queueClient).sendMessage(messageCaptor.capture());

        ServiceBusMessage message = messageCaptor.getValue();

        assertThat(message.getMessageId()).isEqualTo(envelopeId);
        assertThat(message.getContentType()).isEqualTo("application/json");
        String messageBodyJson = message.getBody().toString();
        String expectedMessageBodyJson =
            String.format(
                "{\"envelope_id\":\"%s\",\"ccd_id\":\"%s\",\"envelope_ccd_action\":\"%s\"}",
                envelopeId,
                ccdId,
                envelopeCcdAction
            );
        JSONAssert.assertEquals(expectedMessageBodyJson, messageBodyJson, JSONCompareMode.STRICT);
    }

    @Test
    void notify_should_throw_exception_when_queue_client_fails() throws Exception {
        ServiceBusException exceptionToThrow = new ServiceBusException(
            new IllegalStateException("test exceptipn"),
            ServiceBusErrorSource.UNKNOWN
        );
        willThrow(exceptionToThrow).given(queueClient).sendMessage(any());

        assertThatThrownBy(() -> notifier.notify("envelopeId123", 2321L, EXCEPTION_RECORD))
            .isInstanceOf(NotificationSendingException.class)
            .hasMessage("An error occurred when trying to send notification about successfully processed envelope")
            .hasCause(exceptionToThrow);
    }
}
