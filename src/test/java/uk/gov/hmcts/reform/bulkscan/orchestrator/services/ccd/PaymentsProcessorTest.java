package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.PaymentsPublisher;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model.PaymentsData;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentsProcessorTest {
    @Mock
    private PaymentsPublisher paymentsPublisher;

    private Envelope envelope;

    private CaseDetails caseDetails;

    private PaymentsProcessor paymentsProcessor;

    @BeforeEach
    void setUp() {
        paymentsProcessor = new PaymentsProcessor(paymentsPublisher);
    }

    @Test
    void calls_payments_publisher_if_envelope_contains_payments() {
        // given
        envelope = SampleData.envelope(1,
            asList(new Payment("dcn1")),
            emptyList(),
            emptyList()
        );
        caseDetails = CaseDetails.builder().id(20L).build();
        ArgumentCaptor<PaymentsData> paymentsDataCaptor = ArgumentCaptor.forClass(PaymentsData.class);

        // when
        paymentsProcessor.processPayments(envelope, caseDetails.getId(), true);

        // then
        verify(paymentsPublisher).publishPayments(paymentsDataCaptor.capture());
        PaymentsData paymentsData = paymentsDataCaptor.getValue();
        assertThat(paymentsData.ccdReference).isEqualTo(Long.toString(caseDetails.getId()));
        assertThat(paymentsData.jurisdiction).isEqualTo(envelope.jurisdiction);
        assertThat(paymentsData.poBox).isEqualTo(envelope.poBox);
        assertThat(paymentsData.isExceptionRecord).isTrue();
        assertThat(paymentsData.payments.size()).isEqualTo(envelope.payments.size());
        assertThat(paymentsData.payments.get(0).documentControlNumber)
            .isEqualTo(envelope.payments.get(0).documentControlNumber);
    }

    @Test
    void does_not_call_payments_publisher_if_envelope_contains_zero_payments() {
        // given
        envelope = SampleData.envelope(1,
            emptyList(),
            emptyList(),
            emptyList()
        );
        caseDetails = CaseDetails.builder().id(20L).build();

        // when
        paymentsProcessor.processPayments(envelope, caseDetails.getId(), true);

        // then
        verify(paymentsPublisher, never()).publishPayments(any());
    }

    @Test
    void does_not_call_payments_publisher_if_envelope_contains_null_payments() {
        // given
        envelope = SampleData.envelope(1,
            null,
            emptyList(),
            emptyList()
        );
        caseDetails = CaseDetails.builder().id(20L).build();

        // when
        paymentsProcessor.processPayments(envelope, caseDetails.getId(), true);

        // then
        verify(paymentsPublisher, never()).publishPayments(any());
    }
}
