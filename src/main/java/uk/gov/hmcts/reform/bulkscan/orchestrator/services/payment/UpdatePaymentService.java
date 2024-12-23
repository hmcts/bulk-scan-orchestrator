package uk.gov.hmcts.reform.bulkscan.orchestrator.services.payment;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePaymentRepository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment.UpdatePayment;

import java.time.Instant;
import java.util.List;

@Service
public class UpdatePaymentService {

    private final UpdatePaymentRepository updatePaymentRepository;

    public UpdatePaymentService(UpdatePaymentRepository updatePaymentRepository) {
        this.updatePaymentRepository = updatePaymentRepository;
    }

    public void savePayment(UpdatePayment updatePayment) {
        updatePaymentRepository.save(new uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePayment(
            Instant.now(),
            updatePayment.exceptionRecordRef,
            updatePayment.envelopeId,
            updatePayment.newCaseRef,
            updatePayment.jurisdiction,
            updatePayment.status
            ));
    }

    public List<UpdatePayment> getUpdatePaymentByStatus(String status) {

        return updatePaymentRepository.getUpdatePaymentsByStatus(status).stream().map(UpdatePayment::new).toList();
    }

    public void updateStatusByEnvelopeId(String status, String envelopeId) {

        updatePaymentRepository.updateStatusByEnvelopeId(status, envelopeId);
    }
}
