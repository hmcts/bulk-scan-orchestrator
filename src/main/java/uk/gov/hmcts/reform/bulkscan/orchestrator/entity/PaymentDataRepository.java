package uk.gov.hmcts.reform.bulkscan.orchestrator.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentDataRepository extends JpaRepository<PaymentData, UUID> {

    Optional<List<PaymentData>> findPaymentDataByPayment(Payment payment);
}
