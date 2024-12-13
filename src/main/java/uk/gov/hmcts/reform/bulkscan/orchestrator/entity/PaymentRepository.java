package uk.gov.hmcts.reform.bulkscan.orchestrator.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> getPaymentsByStatus(String status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Payment SET status = :status WHERE envelopeId = :envelopeId")
    void updateStatusByEnvelopeId(String status, String envelopeId);

}
