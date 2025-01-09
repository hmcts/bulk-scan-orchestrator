package uk.gov.hmcts.reform.bulkscan.orchestrator.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments_data")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Instant createdAt;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private String documentControlNumber;


    public PaymentData(
        Instant createdAt,
        String documentControlNumber

    ) {
        this.createdAt = createdAt;
        this.documentControlNumber = documentControlNumber;
    }

}
