package uk.gov.hmcts.reform.bulkscan.orchestrator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class UpdatePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", insertable = false, updatable = false, nullable = false)
    private UUID id;

    private String envelopeId;

    private String jurisdiction;

    private String exceptionRecordRef;

    private String newCaseRef;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String statusMessage;

    @CreatedDate
    @Setter(AccessLevel.NONE)
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Setter(AccessLevel.NONE)
    private LocalDateTime lastUpdatedAt;

    public UpdatePayment(UpdatePaymentDTO dto, PaymentStatus status) {
        this.envelopeId = dto.getEnvelopeId();
        this.jurisdiction = dto.getJurisdiction();
        this.exceptionRecordRef = dto.getExceptionRecordRef();
        this.newCaseRef = dto.getNewCaseRef();
        this.status = status;
    }
}
