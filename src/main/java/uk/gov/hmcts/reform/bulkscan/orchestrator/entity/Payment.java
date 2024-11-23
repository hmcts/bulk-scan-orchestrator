package uk.gov.hmcts.reform.bulkscan.orchestrator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Instant createdAt;

    private String envelopeId;

    private String ccdReference;

    private String jurisdiction;

    private String service;

    private String poBox;

    private String status;

    private boolean isExceptionRecord;

    @OneToMany(mappedBy = "payment", orphanRemoval = true)
    private List<PaymentData> paymentData;


    public Payment(
        Instant createdAt,
        String envelopeId,
        String ccdReference,
        String jurisdiction,
        String service,
        String poBox,
        String status,
        boolean isExceptionRecord,
        List<PaymentData> paymentData) {

        this.createdAt = createdAt;
        this.envelopeId = envelopeId;
        this.ccdReference = ccdReference;
        this.jurisdiction = jurisdiction;
        this.service = service;
        this.poBox = poBox;
        this.status = status;
        this.isExceptionRecord = isExceptionRecord;
        this.paymentData = paymentData;
    }

}
