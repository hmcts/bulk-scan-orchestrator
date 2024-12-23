package uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.SampleData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.callback.PaymentsHelper;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.ExceptionRecordFields;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.ccd.definition.YesNoFieldValues;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.PaymentService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.UpdatePaymentService;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Envelope;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model.Payment;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = PaymentsProcessor.class)
class PaymentsProcessorTest {
    private static final long CCD_REFERENCE = 20L;
    private static final String SERVICE = "service";
    private static final String CASE_ID = "123";
    private static final String NEW_CASE_ID = "1";
    private static final String CASE_TYPE_ID = SERVICE + "_ExceptionRecord";

    @Autowired
    private PaymentsProcessor paymentsProcessor;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private UpdatePaymentService updatePaymentService;


    @Test
    void saves_payment_to_database_if_envelope_contains_payments() {
        // given
        Envelope envelope = SampleData.envelope(
            1,
            List.of(new Payment("dcn1")),
            emptyList(),
            emptyList()
        );
        ArgumentCaptor<uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment> paymentArgumentCaptor = ArgumentCaptor.forClass(
            uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment.class);

        // when
        paymentsProcessor.createPayments(envelope, CCD_REFERENCE, true);

        // then
        verify(paymentService, atLeastOnce()).savePayment(paymentArgumentCaptor.capture());
        assertThat(paymentArgumentCaptor.getValue().getCcdReference()).isEqualTo(Long.toString(CCD_REFERENCE));
        assertThat(paymentArgumentCaptor.getValue().getJurisdiction()).isEqualTo(envelope.jurisdiction);
        assertThat(paymentArgumentCaptor.getValue().getPoBox()).isEqualTo(envelope.poBox);
        assertThat(paymentArgumentCaptor.getValue().isExceptionRecord()).isTrue();
        assertThat(paymentArgumentCaptor.getValue().getPayments().size()).isEqualTo(envelope.payments.size());
        assertThat(paymentArgumentCaptor.getValue().getPayments().getFirst().documentControlNumber)
        .isEqualTo(envelope.payments.getFirst().documentControlNumber);
    }

    @Test
    void does_not_save_payment_to_database_if_envelope_contains_no_payments() {
        // given
        Envelope envelope = SampleData.envelope(
            1,
            emptyList(),
            emptyList(),
            emptyList()
        );

        // when
        paymentsProcessor.createPayments(envelope, CCD_REFERENCE, true);

        // then
        verify(paymentService, never()).savePayment(any());
    }


    @Test
    void should_save_update_payment_when_case_has_payments() {
        // given
        Map<String, Object> data = new HashMap<>();

        String envelopeId = "987";
        String jurisdiction = "sample jurisdiction";

        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.YES);
        data.put(ExceptionRecordFields.ENVELOPE_ID, envelopeId);

        CaseDetails caseDetails =
            TestCaseBuilder
                .createCaseWith(builder -> builder
                    .id(Long.valueOf(CASE_ID))
                    .caseTypeId(CASE_TYPE_ID)
                    .jurisdiction("some jurisdiction")
                    .data(data)
                );

        // when
        paymentsProcessor.updatePayments(PaymentsHelper.create(caseDetails), CASE_ID, jurisdiction, NEW_CASE_ID);

        // then
        ArgumentCaptor<UpdatePayment> updatePaymentArgumentCaptor = ArgumentCaptor.forClass(UpdatePayment.class);
        verify(updatePaymentService, atLeastOnce()).savePayment(updatePaymentArgumentCaptor.capture());
        assertThat(updatePaymentArgumentCaptor.getValue().getExceptionRecordRef()).isEqualTo(CASE_ID);
        assertThat(updatePaymentArgumentCaptor.getValue().getNewCaseRef()).isEqualTo(NEW_CASE_ID);
        assertThat(updatePaymentArgumentCaptor.getValue().getEnvelopeId()).isEqualTo(envelopeId);
        assertThat(updatePaymentArgumentCaptor.getValue().getJurisdiction()).isEqualTo(jurisdiction);
    }

    @Test
    void should_not_save_update_payment_when_case_has_no_payments() {
        // given
        Map<String, Object> data = new HashMap<>();

        String envelopeId = "987";
        String jurisdiction = "sample jurisdiction";

        data.put(ExceptionRecordFields.CONTAINS_PAYMENTS, YesNoFieldValues.NO); // no payments!
        data.put(ExceptionRecordFields.ENVELOPE_ID, envelopeId);

        CaseDetails caseDetails =
            TestCaseBuilder
                .createCaseWith(builder -> builder
                    .id(Long.valueOf(CASE_ID))
                    .caseTypeId(CASE_TYPE_ID)
                    .jurisdiction("some jurisdiction")
                    .data(data)
                );


        // when
        paymentsProcessor.updatePayments(PaymentsHelper.create(caseDetails), CASE_ID, jurisdiction, NEW_CASE_ID);

        // then
        verify(updatePaymentService, never()).savePayment(any());
    }
}
