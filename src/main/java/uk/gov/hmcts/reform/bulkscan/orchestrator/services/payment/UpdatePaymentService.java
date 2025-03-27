package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePaymentRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Service layer for retrieving/persisting payment updates.
 */
@Service
public class UpdatePaymentService {

    private static final Logger log = LoggerFactory.getLogger(UpdatePaymentService.class);
    private final UpdatePaymentRepository updatePaymentRepository;

    public UpdatePaymentService(UpdatePaymentRepository updatePaymentRepository) {
        this.updatePaymentRepository = updatePaymentRepository;
    }

    @Transactional
    public void savePayment(UpdatePayment updatePayment) {
        updatePaymentRepository.save(new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePayment(
            updatePayment.createdAt,
            updatePayment.exceptionRecordRef,
            updatePayment.envelopeId,
            updatePayment.newCaseRef,
            updatePayment.jurisdiction,
            updatePayment.status
            ));
    }

    public List<UpdatePayment> getUpdatePaymentByStatus(String status) {
        log.info("Update payment by Status: {}", updatePaymentRepository.getUpdatePaymentsByStatus(status));
        return updatePaymentRepository.getUpdatePaymentsByStatus(status).stream().map(UpdatePayment::new).toList();
    }

    @Transactional
    public void updateStatusByEnvelopeId(String status, String envelopeId) {

        updatePaymentRepository.updateStatusByEnvelopeId(status, envelopeId);
    }

    public List<UpdatePayment> getAllByPaymentsForPreviousDay() {
        Instant startOfPreviousDay = LocalDateTime.of(
            LocalDate.now().minusDays(1), LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC);
        Instant endOfPreviousDay = LocalDateTime.of(
            LocalDate.now().minusDays(1), LocalTime.MAX).toInstant(ZoneOffset.UTC);
        return updatePaymentRepository.findAllWithDatesBetween(startOfPreviousDay, endOfPreviousDay)
            .stream().map(UpdatePayment::new).toList();

    }

    public List<UpdatePayment> getAllByPaymentsByDate(LocalDate date) {
        Instant startOfPreviousDay = LocalDateTime.of(
            date, LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC);
        Instant endOfPreviousDay = LocalDateTime.of(
            date, LocalTime.MAX).toInstant(ZoneOffset.UTC);
        return updatePaymentRepository.findAllWithDatesBetween(startOfPreviousDay, endOfPreviousDay)
            .stream().map(UpdatePayment::new).toList();
    }
}
