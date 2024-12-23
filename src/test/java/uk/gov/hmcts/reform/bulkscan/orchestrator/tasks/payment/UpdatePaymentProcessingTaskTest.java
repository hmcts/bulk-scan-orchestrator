package uk.gov.hmcts.reform.bulkscan.orchestrator.tasks.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.bulkscan.orchestrator.client.payment.PaymentApiClient;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment.UpdatePaymentService;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
public class UpdatePaymentProcessingTaskTest {


    private static final uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePayment updatePaymentEntity1 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePayment(
            Instant.now(),
            "123456",
            "7891234" ,
            "987654321",
            "jurisdiction1",
            "awaiting"
        );

    private static final  uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePayment updatePaymentEntity2 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePayment(
            Instant.now(),
            "123456",
            "7891234" ,
            "987654321",
            "jurisdiction1",
            "awaiting"
        );

    private static final UpdatePayment updatePayment1 =
        new UpdatePayment(updatePaymentEntity1);

    private static final UpdatePayment updatePayment2 =
        new UpdatePayment(updatePaymentEntity2);

    private UpdatePaymentProcessingTask updatePaymentProcessingTask;

    @Mock
    private UpdatePaymentService updatePaymentService;

    @Mock
    private PaymentApiClient paymentApiClient;

    @BeforeEach
    void setUp() {
        int retryCount = 3;
        updatePaymentProcessingTask = new UpdatePaymentProcessingTask(updatePaymentService ,paymentApiClient, retryCount);
    }

    @Test
    void should_not_post_when_no_payment_with_status_awaiting() {
        given(updatePaymentService.getUpdatePaymentByStatus(any())).willReturn(Collections.emptyList());
        updatePaymentProcessingTask.processUpdatePayments();
        verify(paymentApiClient, never()).postPayment(any());
    }

    @Test
    void should_post_when_payments_with_status_awaiting() {

        List<UpdatePayment> paymentList = Arrays.asList(updatePayment1, updatePayment2);

        given(updatePaymentService.getUpdatePaymentByStatus("awaiting")).willReturn(paymentList);
        given(paymentApiClient.postUpdatePayment(any())).willReturn( new ResponseEntity<>("body", HttpStatus.OK));
        updatePaymentProcessingTask.processUpdatePayments();
        verify(paymentApiClient, times(2)).postUpdatePayment(any());
    }


    @Test
    void should_retry_three_times_when_posting_payment() {

        List<UpdatePayment> paymentList = List.of(updatePayment1);

        given(updatePaymentService.getUpdatePaymentByStatus("awaiting")).willReturn(paymentList);
        given(paymentApiClient.postUpdatePayment(any())).willReturn( new ResponseEntity<>("body", HttpStatus.BAD_REQUEST));
        updatePaymentProcessingTask.processUpdatePayments();
        verify(paymentApiClient, times(3)).postUpdatePayment(any());
    }

    @Test
    void should_update_status_after_successfully_posting_payment() {

        List<UpdatePayment> paymentList = List.of(updatePayment1);

        given(updatePaymentService.getUpdatePaymentByStatus("awaiting")).willReturn(paymentList);
        given(paymentApiClient.postUpdatePayment(any())).willReturn( new ResponseEntity<>("body", HttpStatus.OK));
        updatePaymentProcessingTask.processUpdatePayments();
        verify(updatePaymentService, times(1)).updateStatusByEnvelopeId("success","987654321");
    }

    @Test
    void should_update_status_after_unsuccessfully_posting_payment() {

        List<UpdatePayment> paymentList = List.of(updatePayment1);

        given(updatePaymentService.getUpdatePaymentByStatus("awaiting")).willReturn(paymentList);
        given(paymentApiClient.postUpdatePayment(any())).willReturn( new ResponseEntity<>("body", HttpStatus.BAD_REQUEST));
        updatePaymentProcessingTask.processUpdatePayments();
        verify(updatePaymentService, times(1)).updateStatusByEnvelopeId("error","987654321");
    }

}
