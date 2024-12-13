//package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus;
//
//import com.azure.messaging.servicebus.ServiceBusErrorSource;
//import com.azure.messaging.servicebus.ServiceBusException;
//import com.azure.messaging.servicebus.ServiceBusMessage;
//import com.azure.messaging.servicebus.ServiceBusSenderClient;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.MethodSource;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.skyscreamer.jsonassert.JSONAssert;
//import org.skyscreamer.jsonassert.JSONCompareMode;
//import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.Labels;
//import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublisher;
//import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
//import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;
//import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentData;
//import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.UpdatePaymentsCommand;
//
//import java.time.Instant;
//import java.util.List;
//
//import static java.util.Arrays.asList;
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.BDDMockito.willThrow;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.verify;
//
//@SuppressWarnings("checkstyle:LineLength")
//@ExtendWith(MockitoExtension.class)
//class PaymentsPublisherTest {
//
//    private static Object[][] getIsExceptionRecord() {
//        return new Object[][] {
//            {true},
//            {false}
//        };
//    }
//
//    private PaymentsPublisher paymentsPublisher;
//
//    @Mock
//    private ServiceBusSenderClient queueClient;
//
//    @BeforeEach
//    void setUp() {
//        paymentsPublisher = new PaymentsPublisher(queueClient, new ObjectMapper(), 2, 1000);
//    }
//
//    @ParameterizedTest
//    @MethodSource("getIsExceptionRecord")
//    void sending_create_command_should_send_message_with_right_content(boolean isExceptionRecord) throws Exception {
//        // given
//        CreatePaymentsCommand cmd = getCreatePaymentsCommand(isExceptionRecord);
//        Instant startTime = Instant.now();
//
//        // when
//        paymentsPublisher.send(cmd);
//
//        // then
//        ArgumentCaptor<ServiceBusMessage> messageCaptor = ArgumentCaptor.forClass(ServiceBusMessage.class);
//        verify(queueClient).sendMessage(messageCaptor.capture());
//
//        ServiceBusMessage message = messageCaptor.getValue();
//
//        assertThat(message.getContentType()).isEqualTo("application/json");
//        assertThat(message.getSubject()).isEqualTo(Labels.CREATE);
//
//        String messageBodyJson = message.getBody().toString();
//        String expectedMessageBodyJson = String.format(
//            "{\"envelope_id\":\"%s\", \"ccd_reference\":\"%s\", \"jurisdiction\":\"%s\", \"service\":\"%s\", "
//                + "\"po_box\":\"%s\", " + "\"is_exception_record\":%s, "
//                + "\"payments\":[{\"document_control_number\":\"%s\"}]}",
//            cmd.envelopeId,
//            cmd.ccdReference,
//            cmd.jurisdiction,
//            cmd.service,
//            cmd.poBox,
//            Boolean.toString(cmd.isExceptionRecord),
//            cmd.payments.get(0).documentControlNumber
//        );
//        JSONAssert.assertEquals(expectedMessageBodyJson, messageBodyJson, JSONCompareMode.LENIENT);
//    }
//
//    @ParameterizedTest
//    @MethodSource("getIsExceptionRecord")
//    void sending_create_command_should_throw_exception_when_queue_client_fails(boolean isExceptionRecord) {
//        CreatePaymentsCommand cmd = getCreatePaymentsCommand(isExceptionRecord);
//
//        ServiceBusException exceptionToThrow
//            = new ServiceBusException(new IllegalAccessException("test"), ServiceBusErrorSource.UNKNOWN);
//        willThrow(exceptionToThrow).given(queueClient).sendMessage(any());
//
//        assertThatThrownBy(() -> paymentsPublisher.send(cmd))
//            .isInstanceOf(PaymentsPublishingException.class)
//            .hasMessageContaining("An error occurred when trying to publish message to payments queue.")
//            .hasCause(exceptionToThrow);
//
//        verify(queueClient, times(3)).sendMessage(any());
//
//    }
//
//    @Test
//    void sending_update_command_should_message_with_correct_content() throws Exception {
//        // given
//        UpdatePaymentsCommand cmd =
//            new UpdatePaymentsCommand(
//                "er-ref",
//                "new-case-ref",
//                "envelope-id",
//                "jurisdiction"
//            );
//
//        // when
//        paymentsPublisher.send(cmd);
//
//        // then
//        ArgumentCaptor<ServiceBusMessage> messageCaptor = ArgumentCaptor.forClass(ServiceBusMessage.class);
//        verify(queueClient).sendMessage(messageCaptor.capture());
//
//        ServiceBusMessage msg = messageCaptor.getValue();
//
//        assertThat(msg.getSubject()).isEqualTo(Labels.UPDATE);
//
//        JSONAssert.assertEquals(
//            (
//                "{"
//                    + "'exception_record_ref': 'er-ref',"
//                    + "'new_case_ref': 'new-case-ref',"
//                    + "'envelope_id': 'envelope-id',"
//                    + "'jurisdiction': 'jurisdiction'"
//                    + "}"
//            ).replace("'", "\""),
//            msg.getBody().toString(),
//            JSONCompareMode.LENIENT
//        );
//    }
//
//    @Test
//    void sending_create_command_should_retry_for_transient_exception_and_should_recover() {
//        CreatePaymentsCommand cmd = getCreatePaymentsCommand(true);
//
//        ServiceBusException exceptionToThrow =
//            new ServiceBusException(new IllegalStateException("test exception"), ServiceBusErrorSource.UNKNOWN);
//        willThrow(exceptionToThrow).willDoNothing().given(queueClient).sendMessage(any());
//
//        paymentsPublisher.send(cmd);
//
//        ArgumentCaptor<ServiceBusMessage> messageCaptor = ArgumentCaptor.forClass(ServiceBusMessage.class);
//        verify(queueClient, times(2)).sendMessage(messageCaptor.capture());
//
//        List<ServiceBusMessage> capturedMessage = messageCaptor.getAllValues();
//
//        ServiceBusMessage msg1 = capturedMessage.get(0);
//        ServiceBusMessage msg2 = capturedMessage.get(1);
//
//        assertThat(msg1).isSameAs(msg2);
//
//    }
//
//    private CreatePaymentsCommand getCreatePaymentsCommand(boolean isExceptionRecord) {
//        return new CreatePaymentsCommand(
//            "envelope-id",
//            Long.toString(10L),
//            "jurisdiction",
//            "service",
//            "pobox",
//            isExceptionRecord,
//            asList(new PaymentData("dcn1"))
//        );
//    }
//}
