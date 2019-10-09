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
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.Labels;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublishingException;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.PaymentData;

import java.time.Instant;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@SuppressWarnings("checkstyle:LineLength")
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
    void sending_create_command_should_send_message_with_right_content(boolean isExceptionRecord) throws Exception {
        // given
        CreatePaymentsCommand cmd = getCreatePaymentsCommand(isExceptionRecord);
        Instant startTime = Instant.now();

        // when
        paymentsPublisher.send(cmd);

        // then
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(queueClient).scheduleMessage(messageCaptor.capture(), instantCaptor.capture());

        assertThat(instantCaptor.getValue()).isAfterOrEqualTo(startTime.plusSeconds(10));
        assertThat(instantCaptor.getValue()).isBefore(startTime.plusSeconds(11));

        Message message = messageCaptor.getValue();

        assertThat(message.getMessageId()).isEqualTo(cmd.ccdReference);
        assertThat(message.getContentType()).isEqualTo("application/json");
        assertThat(message.getLabel()).isEqualTo(Labels.CREATE);

        String messageBodyJson = new String(MessageBodyRetriever.getBinaryData(message.getMessageBody()));
        String expectedMessageBodyJson = String.format(
            "{\"envelope_id\":\"%s\", \"ccd_reference\":\"%s\", \"jurisdiction\":\"%s\", \"service\":\"%s\", "
                + "\"po_box\":\"%s\", " + "\"is_exception_record\":%s, "
                + "\"payments\":[{\"document_control_number\":\"%s\"}]}",
            cmd.envelopeId,
            cmd.ccdReference,
            cmd.jurisdiction,
            cmd.service,
            cmd.poBox,
            Boolean.toString(cmd.isExceptionRecord),
            cmd.payments.get(0).documentControlNumber
        );
        JSONAssert.assertEquals(expectedMessageBodyJson, messageBodyJson, JSONCompareMode.LENIENT);
    }

    @ParameterizedTest
    @MethodSource("getIsExceptionRecord")
    void sending_create_command_should_throw_exception_when_queue_client_fails(boolean isExceptionRecord) throws Exception {
        CreatePaymentsCommand cmd = getCreatePaymentsCommand(isExceptionRecord);

        ServiceBusException exceptionToThrow = new ServiceBusException(true, "test exception");
        willThrow(exceptionToThrow).given(queueClient).scheduleMessage(any(), any());

        assertThatThrownBy(() -> paymentsPublisher.send(cmd))
            .isInstanceOf(PaymentsPublishingException.class)
            .hasMessage(
                String.format(
                    "An error occurred when trying to publish payments for CCD Ref %s",
                    cmd.ccdReference
                )
            )
            .hasCause(exceptionToThrow);
    }

    private CreatePaymentsCommand getCreatePaymentsCommand(boolean isExceptionRecord) {
        return new CreatePaymentsCommand(
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
