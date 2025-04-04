package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePayment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePaymentRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = UpdatePaymentService.class)
public class UpdatePaymentServiceTest {

    @Autowired
    private UpdatePaymentService updatePaymentService;

    @MockBean
    private UpdatePaymentRepository updatePaymentRepository;

    final UpdatePayment updatePaymentEntity1 =
        new UpdatePayment(
            Instant.now(),
            "exceptionRecordRef",
            "newCaseRef",
            "envelope_id",
            "jurisdiction",
            "awaiting"
        );
    uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment updatePayment1 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment(updatePaymentEntity1);

    final UpdatePayment updatePaymentEntity2 =
        new UpdatePayment(
            Instant.now(),
            "exceptionRecordRef",
            "newCaseRef",
            "envelope_id",
            "jurisdiction",
            "awaiting"
        );
    uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment updatePayment2 =
        new uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment(updatePaymentEntity2);

    @BeforeEach
    void setUp() {
        updatePaymentService = new UpdatePaymentService(updatePaymentRepository);
    }

    @Test
    void shouldAddPayment() {

        // when
        updatePaymentService.savePayment(updatePayment1);

        //then
        verify(updatePaymentRepository).save(any());
    }

    @Test
    void shouldGetPaymentsByStatus() {

        final String status = "status";
        when(updatePaymentRepository.getUpdatePaymentsByStatus(status))
            .thenReturn(List.of(updatePaymentEntity1,updatePaymentEntity1));

        //when
        List<uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment> results =
            updatePaymentService.getUpdatePaymentByStatus(status);

        //then
        assertThat(results.size()).isEqualTo(2);

    }

    @Test
    void shouldUpdateStatusByEnvelopeId() {

        final String status = "success";
        // when
        updatePaymentService.updateStatusByEnvelopeId(status, updatePayment1.getEnvelopeId());

        //then
        verify(updatePaymentRepository)
            .updateStatusByEnvelopeId(status, updatePayment1.getEnvelopeId());
    }

    @Test
    void shouldGetAllByPaymentsForPreviousDay() {
        when(updatePaymentRepository.findAllWithDatesBetween(any(), any()))
            .thenReturn(List.of(updatePaymentEntity1,updatePaymentEntity1));

        //when
        List<uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment> results =
            updatePaymentService.getAllByPaymentsForPreviousDay();

        //then
        assertThat(results.size()).isEqualTo(2);

    }

}
