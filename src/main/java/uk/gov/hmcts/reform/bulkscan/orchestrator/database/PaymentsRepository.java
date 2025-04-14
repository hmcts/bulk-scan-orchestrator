package uk.gov.hmcts.reform.bulkscan.orchestrator.database;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.Payment;
import uk.gov.hmcts.reform.bulkscan.orchestrator.model.PaymentStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentsRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findAllByStatus(@Param("status") PaymentStatus status);
}
