package uk.gov.hmcts.reform.bulkscan.orchestrator.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Responsible for interacting with the payment table.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> getPaymentsByStatus(String status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Payment SET status = :status WHERE envelopeId = :envelopeId")
    void updateStatusByEnvelopeId(String status, String envelopeId);

    @Query("SELECT p from Payment p where p.createdAt <= :creationDateTime")
    List<Payment> findAllByCreatedAt(Instant creationDateTime);

}
