package uk.gov.hmcts.reform.bulkscan.orchestrator.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> getPaymentsByStatus(String status);

    Payment updatePaymentByStatus(String status, Payment payment);

}
