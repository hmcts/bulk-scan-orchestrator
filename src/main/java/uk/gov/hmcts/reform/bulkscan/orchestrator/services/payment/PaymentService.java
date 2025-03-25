package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Service layer for retrieving and persisting payments.
 */
@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public void savePayment(Payment payment) {
        uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment
            paymentEntity = new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment(
            Instant.now(),
            payment.envelopeId,
            payment.ccdReference,
            payment.jurisdiction,
            payment.service,
            payment.poBox,
            payment.status,
            payment.isExceptionRecord,
            payment.payments.stream().map(p -> new PaymentData(Instant.now(), p.documentControlNumber)).toList()
        );

        //Associate payment data to the payment parent
        for (PaymentData paymentData: paymentEntity.getPaymentData()) {
            paymentData.setPayment(paymentEntity);
        }

        paymentRepository.save(paymentEntity);
    }

    public List<Payment> getPaymentsByStatus(String status) {

        log.info("Payments by Status" + paymentRepository.getPaymentsByStatus(status).toString());
        return paymentRepository.getPaymentsByStatus(status).stream().map(Payment::new).toList();
    }

    @Transactional
    public void updateStatusByEnvelopeId(String status, String envelopeId) {

        paymentRepository.updateStatusByEnvelopeId(status, envelopeId);
    }

    public List<Payment> getAllByPaymentsForPreviousDay() {
        return paymentRepository.findAllByCreatedAt(
                LocalDateTime.now().minusDays(1).toInstant(ZoneOffset.UTC))
            .stream().map(Payment::new).toList();
    }

}
