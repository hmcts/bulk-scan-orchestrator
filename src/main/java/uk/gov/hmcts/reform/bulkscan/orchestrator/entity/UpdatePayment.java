package uk.gov.hmcts.reform.bulkscan.orchestrator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Model representing an item in the update payment table.
 */
@Entity
@Table(name = "update_payments")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePayment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Instant createdAt;

    private String exceptionRecordRef;

    private String newCaseRef;

    private String envelopeId;

    private String jurisdiction;

    private String status;


    public UpdatePayment(
        Instant createdAt,
        String exceptionRecordRef,
        String newCaseRef,
        String envelopeId,
        String jurisdiction,
        String status) {

        this.createdAt = createdAt;
        this.exceptionRecordRef = exceptionRecordRef;
        this.newCaseRef = newCaseRef;
        this.envelopeId = envelopeId;
        this.jurisdiction = jurisdiction;
        this.status = status;
    }
}
