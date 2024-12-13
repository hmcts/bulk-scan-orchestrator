package uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.List;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Data
@AllArgsConstructor
public class Payment {

    @JsonProperty("envelope_id")
    public final String envelopeId;

    @JsonProperty("ccd_reference")
    public final String ccdReference;

    @JsonProperty("jurisdiction")
    public final String jurisdiction;

    @JsonProperty("service")
    public final String service;

    @JsonProperty("po_box")
    public final String poBox;

    @JsonProperty("is_exception_record")
    public final boolean isExceptionRecord;

    @JsonProperty("status")
    public final String status;

    @JsonProperty("payments")
    public final List<PaymentData> payments;



    public Payment(final uk.gov.hmcts.reform.bulkscan.orchestrator.entity.Payment payment) {
        this.envelopeId = payment.getEnvelopeId();
        this.ccdReference = payment.getCcdReference();
        this.jurisdiction = payment.getJurisdiction();
        this.service = payment.getService();
        this.poBox = payment.getPoBox();
        this.isExceptionRecord = payment.isExceptionRecord();
        this.status = payment.getStatus();
        this.payments = ofNullable(payment.getPaymentData()).stream().flatMap(Collection::stream)
            .map(PaymentData::new).collect(toList());
    }

}
