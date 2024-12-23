package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment.PaymentApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.PaymentService;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
public class PaymentProcessingTaskTest {

    private static final PaymentData paymentData = new PaymentData(Instant.now(),"123");

    private static final uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment paymentEntity1 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment(
            Instant.now(),
            "123",
            "234",
            "jurisdiction1",
            "service1",
            "poBox1",
            "awaiting",
            true,
            Collections.singletonList(paymentData)
        );

    private static final  uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment paymentEntity2 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment(
            Instant.now(),
            "456",
            "789",
            "jurisdiction2",
            "service2",
            "poBox2",
            "awaiting",
            true,
            Collections.singletonList(paymentData)
        );

    private static final uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment payment1 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment(paymentEntity1);

    private static final uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment payment2 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment(paymentEntity2);

    private PaymentProcessingTask paymentProcessingTask;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentApiClient paymentApiClient;

    @BeforeEach
    void setUp() {
        int retryCount = 3;
        paymentProcessingTask = new PaymentProcessingTask(paymentService ,paymentApiClient, retryCount);
    }

    @Test
    void should_not_post_when_no_payment_with_status_awaiting() {
        given(paymentService.getPaymentsByStatus(any())).willReturn(Collections.emptyList());
        paymentProcessingTask.processPayments();
        verify(paymentApiClient, never()).postPayment(any());
    }

    @Test
    void should_post_when_payments_with_status_awaiting() {

        List<Payment> paymentList = Arrays.asList(payment1, payment2);

        given(paymentService.getPaymentsByStatus("awaiting")).willReturn(paymentList);
        given(paymentApiClient.postPayment(any())).willReturn( new ResponseEntity<>("body", HttpStatus.OK));
        paymentProcessingTask.processPayments();
        verify(paymentApiClient, times(2)).postPayment(any());
    }


    @Test
    void should_retry_three_times_when_posting_payment() {

        List<Payment> paymentList = List.of(payment1);

        given(paymentService.getPaymentsByStatus("awaiting")).willReturn(paymentList);
        given(paymentApiClient.postPayment(any())).willReturn( new ResponseEntity<>("body", HttpStatus.BAD_REQUEST));
        paymentProcessingTask.processPayments();
        verify(paymentApiClient, times(3)).postPayment(any());
    }

    @Test
    void should_update_status_after_successfully_posting_payment() {

        List<Payment> paymentList = List.of(payment1);

        given(paymentService.getPaymentsByStatus("awaiting")).willReturn(paymentList);
        given(paymentApiClient.postPayment(any())).willReturn( new ResponseEntity<>("body", HttpStatus.OK));
        paymentProcessingTask.processPayments();
        verify(paymentService, times(1)).updateStatusByEnvelopeId("success","123");
    }

    @Test
    void should_update_status_after_unsuccessfully_posting_payment() {

        List<Payment> paymentList = List.of(payment1);

        given(paymentService.getPaymentsByStatus("awaiting")).willReturn(paymentList);
        given(paymentApiClient.postPayment(any())).willReturn( new ResponseEntity<>("body", HttpStatus.BAD_REQUEST));
        paymentProcessingTask.processPayments();
        verify(paymentService, times(1)).updateStatusByEnvelopeId("error","123");
    }

}
