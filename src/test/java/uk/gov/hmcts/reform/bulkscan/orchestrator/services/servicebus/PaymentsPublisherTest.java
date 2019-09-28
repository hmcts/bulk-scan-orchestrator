package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentsData;

import java.time.Instant;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentsPublisherTest {

    private PaymentsPublisher paymentsPublisher;

    @Mock
    private QueueClient queueClient;

    @BeforeEach
    void setUp() {
        paymentsPublisher = new PaymentsPublisher(queueClient, new ObjectMapper());
    }

    @Test
    void notify_should_send_message_with_right_content() throws Exception {
        // given
        PaymentsData paymentsData = getPaymentsData();
        Instant startTime = Instant.now();

        // when
        paymentsPublisher.publishPayments(paymentsData);

        // then
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(queueClient).scheduleMessage(messageCaptor.capture(), instantCaptor.capture());

        assertThat(instantCaptor.getValue()).isAfterOrEqualTo(startTime.plusSeconds(10));
        assertThat(instantCaptor.getValue()).isBefore(startTime.plusSeconds(11));

        Message message = messageCaptor.getValue();

        assertThat(message.getMessageId()).isEqualTo(paymentsData.ccdReference);
        assertThat(message.getContentType()).isEqualTo("application/json");

        String messageBodyJson = new String(MessageBodyRetriever.getBinaryData(message.getMessageBody()));
        String expectedMessageBodyJson = String.format(
            "{\"ccd_reference\":\"%s\", \"jurisdiction\":\"%s\", \"po_box\":\"%s\", "
                + "\"is_exception_record\":%s, "
                + "\"payments\":[{\"document_control_number\":\"%s\"}]}",
            paymentsData.ccdReference,
            paymentsData.jurisdiction,
            paymentsData.poBox,
            Boolean.toString(paymentsData.isExceptionRecord),
            paymentsData.payments.get(0).documentControlNumber
        );
        JSONAssert.assertEquals(expectedMessageBodyJson, messageBodyJson, JSONCompareMode.LENIENT);
    }

    @Test
    void notify_should_throw_exception_when_queue_client_fails() throws Exception {
        PaymentsData paymentsData = getPaymentsData();

        ServiceBusException exceptionToThrow = new ServiceBusException(true, "test exception");
        willThrow(exceptionToThrow).given(queueClient).scheduleMessage(any(), any());

        assertThatThrownBy(() -> paymentsPublisher.publishPayments(paymentsData))
            .isInstanceOf(PaymentsPublishingException.class)
            .hasMessage("An error occurred when trying to publish payments")
            .hasCause(exceptionToThrow);
    }

    private PaymentsData getPaymentsData() {
        return new PaymentsData(
                Long.toString(10L),
                "jurisdiction",
                "pobox",
                true,
                asList(new PaymentData("dcn1"))
            );
    }
}
