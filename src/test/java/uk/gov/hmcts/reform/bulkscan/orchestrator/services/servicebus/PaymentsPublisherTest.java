package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

    private static Object[][] getIsExceptionRecord() {
        return new Object[][]{
            {true},
            {false}
        };
    }

    private PaymentsPublisher paymentsPublisher;

    @Mock
    private QueueClient queueClient;

    @BeforeEach
    void setUp() {
        paymentsPublisher = new PaymentsPublisher(queueClient, new ObjectMapper());
    }

    @ParameterizedTest
    @MethodSource("getIsExceptionRecord")
    void notify_should_send_message_with_right_content(boolean isExceptionRecord) throws Exception {
        // given
        PaymentsData paymentsData = getPaymentsData(isExceptionRecord);
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
            "{\"envelope_id\":\"%s\", \"ccd_reference\":\"%s\", \"jurisdiction\":\"%s\", \"service\":\"%s\", "
                + "\"po_box\":\"%s\", " + "\"is_exception_record\":%s, "
                + "\"payments\":[{\"document_control_number\":\"%s\"}]}",
            paymentsData.envelopeId,
            paymentsData.ccdReference,
            paymentsData.jurisdiction,
            paymentsData.service,
            paymentsData.poBox,
            Boolean.toString(paymentsData.isExceptionRecord),
            paymentsData.payments.get(0).documentControlNumber
        );
        JSONAssert.assertEquals(expectedMessageBodyJson, messageBodyJson, JSONCompareMode.LENIENT);
    }

    @ParameterizedTest
    @MethodSource("getIsExceptionRecord")
    void notify_should_throw_exception_when_queue_client_fails(boolean isExceptionRecord) throws Exception {
        PaymentsData paymentsData = getPaymentsData(isExceptionRecord);

        ServiceBusException exceptionToThrow = new ServiceBusException(true, "test exception");
        willThrow(exceptionToThrow).given(queueClient).scheduleMessage(any(), any());

        assertThatThrownBy(() -> paymentsPublisher.publishPayments(paymentsData))
            .isInstanceOf(PaymentsPublishingException.class)
            .hasMessage(
                String.format(
                    "An error occurred when trying to publish payments for CCD Ref %s",
                    paymentsData.ccdReference
                )
            )
            .hasCause(exceptionToThrow);
    }

    private PaymentsData getPaymentsData(boolean isExceptionRecord) {
        return new PaymentsData(
            "envelope-id",
                Long.toString(10L),
                "jurisdiction",
                "service",
                "pobox",
                isExceptionRecord,
                asList(new PaymentData("dcn1"))
            );
    }
}
