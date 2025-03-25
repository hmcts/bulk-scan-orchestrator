package uk.gov.hmcts.reform.bulkscan.orchestrator.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Responsible for interacting with the update payment table.
 */
@Repository
public interface UpdatePaymentRepository extends JpaRepository<UpdatePayment, UUID> {

    List<UpdatePayment> getUpdatePaymentsByStatus(String status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE UpdatePayment  SET status = :status WHERE envelopeId = :envelopeId")
    void updateStatusByEnvelopeId(String status, String envelopeId);

    @Query("SELECT p from UpdatePayment p where p.createdAt <= :creationDateTime")
    List<UpdatePayment> findAllByCreatedAt(Instant creationDateTime);
}
