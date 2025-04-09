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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", insertable = false, updatable = false, nullable = false)
    private UUID id;

    private String envelopeId;

    private String ccdReference;

    private boolean isExceptionRecord;

    private String poBox;

    private String jurisdiction;

    private String service;

    @Column(columnDefinition = "text[]")
    private List<String> payments;

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

    public Payment(CreatePaymentDTO dto, PaymentStatus status) {
        this.envelopeId = dto.getEnvelopeId();
        this.ccdReference = dto.getCcdReference();
        this.isExceptionRecord = dto.isExceptionRecord();
        this.poBox = dto.getPoBox();
        this.jurisdiction = dto.getJurisdiction();
        this.service = dto.getService();
        this.payments = dto.getPayments()
            .stream()
            .map(PaymentInfoDTO::getDocumentControlNumber)
            .collect(Collectors.toList());
        this.status = status;
    }
}
