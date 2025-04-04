package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = PaymentService.class)
public class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @MockBean
    private PaymentRepository paymentRepository;

    final PaymentData paymentData = new PaymentData(Instant.now(),"123");
    final Payment paymentEntity1 =
        new Payment(
            Instant.now(),
            "envelope_id",
            "ccdReference",
            "jurisdiction",
            "service",
            "poBox",
            "status",
            true,
            Collections.singletonList(paymentData)
        );
    uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment payment1 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment(paymentEntity1);

    final Payment paymentEntity2 =
        new Payment(
        Instant.now(),
            "envelope_id_2",
                "ccdReference_2",
                "jurisdiction_2",
                "service_2",
                "poBox2",
                "status_2",
                true,
                Collections.singletonList(paymentData)
        );
    uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment payment2 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment(paymentEntity2);

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository);
    }

    @Test
    void shouldAddPayment() {

        // when
        paymentService.savePayment(payment1);

        //then
        verify(paymentRepository).save(any());
    }

    @Test
    void shouldGetPaymentsByStatus() {

        final String status = "status";
        when(paymentRepository.getPaymentsByStatus(status)).thenReturn(List.of(paymentEntity1,paymentEntity2));

        //when
        List<uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment> results =
            paymentService.getPaymentsByStatus(status);

        //then
        assertThat(results.size()).isEqualTo(2);

    }

    @Test
    void shouldUpdateStatusByEnvelopeId() {

        final String status = "success";
        // when
        paymentService.updateStatusByEnvelopeId(status, payment1.getEnvelopeId());

        //then
        verify(paymentRepository).updateStatusByEnvelopeId(status, payment1.getEnvelopeId());
    }

    @Test
    void shouldGetAllByPaymentsForPreviousDay() {
        when(paymentRepository.findAllWithDatesBetween(any(), any()))
            .thenReturn(List.of(paymentEntity1,paymentEntity2));

        //when
        List<uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment> results =
            paymentService.getAllByPaymentsForPreviousDay();

        //then
        assertThat(results.size()).isEqualTo(2);

    }

}
