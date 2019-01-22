package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ProcessedEnvelopeNotifierTest {

    private ProcessedEnvelopeNotifier notifier;

    @Mock
    private QueueClient queueClient;

    @Before
    public void setUp() {
        notifier = new ProcessedEnvelopeNotifier(queueClient, new ObjectMapper());
    }

    @Test
    public void notify_should_send_message_with_right_content() throws Exception {
        // given
        String envelopeId = UUID.randomUUID().toString();

        // when
        notifier.notify(envelopeId);

        // then
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(queueClient).send(messageCaptor.capture());
        Message message = messageCaptor.getValue();

        assertThat(message.getMessageId()).isEqualTo(envelopeId);
        assertThat(message.getContentType()).isEqualTo("application/json");

        String messageBodyJson = new String(message.getBody());
        String expectedMessageBodyJson = String.format("{\"id\":\"%s\"}", envelopeId);
        JSONAssert.assertEquals(expectedMessageBodyJson, messageBodyJson, JSONCompareMode.LENIENT);
    }

    @Test
    public void notify_should_throw_exception_when_queue_client_fails() throws Exception {
        ServiceBusException exceptionToThrow = new ServiceBusException(true, "test exception");
        willThrow(exceptionToThrow).given(queueClient).send(any());

        assertThatThrownBy(() -> notifier.notify("envelopeId123"))
            .isInstanceOf(NotificationSendingException.class)
            .hasMessage("An error occurred when trying to send notification about successfully processed envelope")
            .hasCause(exceptionToThrow);
    }
}
