package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.PaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model.CreatePaymentsCommand;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentsProcessorTest {
    private static final long CCD_REFERENCE = 20L;

    @Mock
    private PaymentsPublisher paymentsPublisher;

    private PaymentsProcessor paymentsProcessor;

    @BeforeEach
    void setUp() {
        paymentsProcessor = new PaymentsProcessor(paymentsPublisher);
    }

    @Test
    void calls_payments_publisher_if_envelope_contains_payments() {
        // given
        Envelope envelope = SampleData.envelope(
            1,
            asList(new Payment("dcn1")),
            emptyList(),
            emptyList()
        );
        ArgumentCaptor<CreatePaymentsCommand> paymentsDataCaptor = ArgumentCaptor.forClass(CreatePaymentsCommand.class);

        // when
        paymentsProcessor.processPayments(envelope, CCD_REFERENCE, true);

        // then
        verify(paymentsPublisher).send(paymentsDataCaptor.capture());
        CreatePaymentsCommand createPaymentsCommand = paymentsDataCaptor.getValue();
        assertThat(createPaymentsCommand.ccdReference).isEqualTo(Long.toString(CCD_REFERENCE));
        assertThat(createPaymentsCommand.jurisdiction).isEqualTo(envelope.jurisdiction);
        assertThat(createPaymentsCommand.poBox).isEqualTo(envelope.poBox);
        assertThat(createPaymentsCommand.isExceptionRecord).isTrue();
        assertThat(createPaymentsCommand.payments.size()).isEqualTo(envelope.payments.size());
        assertThat(createPaymentsCommand.payments.get(0).documentControlNumber)
            .isEqualTo(envelope.payments.get(0).documentControlNumber);
    }

    @Test
    void does_not_call_payments_publisher_if_envelope_contains_zero_payments() {
        // given
        Envelope envelope = SampleData.envelope(
            1,
            emptyList(),
            emptyList(),
            emptyList()
        );

        // when
        paymentsProcessor.processPayments(envelope, CCD_REFERENCE, true);

        // then
        verify(paymentsPublisher, never()).send(any());
    }

    @Test
    void does_not_call_payments_publisher_if_envelope_contains_null_payments() {
        // given
        Envelope envelope = SampleData.envelope(
            1,
            null,
            emptyList(),
            emptyList()
        );

        // when
        paymentsProcessor.processPayments(envelope, CCD_REFERENCE, true);

        // then
        verify(paymentsPublisher, never()).send(any());
    }
}
