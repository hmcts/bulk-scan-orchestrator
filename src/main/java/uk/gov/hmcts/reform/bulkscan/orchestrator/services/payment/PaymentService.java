package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentData;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.Payment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public void savePayment(Payment payment) {
        paymentRepository.save(new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment(
            Instant.now(),
            payment.envelopeId,
            payment.ccdReference,
            payment.jurisdiction,
            payment.service,
            payment.poBox,
            payment.status,
            payment.isExceptionRecord,
            payment.payments.stream().map(p -> new PaymentData(Instant.now(), p.documentControlNumber)).toList()
        ));
    }

    public List<Payment> getPaymentsByStatus(String status) {

        return paymentRepository.getPaymentsByStatus(status).stream().map(Payment::new).toList();
    }

    public void updateStatusByEnvelopeId(String status, String envelopeId) {

        paymentRepository.updateStatusByEnvelopeId(status, envelopeId);
    }

    public List<Payment> getAllByPaymentsForPreviousDay() {
        return paymentRepository.findAllByCreatedAt(LocalDate.now().minusDays(1))
            .stream().map(Payment::new).toList();
    }

}
