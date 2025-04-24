package uk.gov.hmcts.reform.bulkscan.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CreatePaymentDTO {

    @JsonProperty(value = "envelope_id", required = true)
    private String envelopeId;

    @JsonProperty(value = "ccd_reference", required = true)
    private String ccdReference;

    @JsonProperty(value = "is_exception_record", required = true)
    private boolean isExceptionRecord;

    @JsonProperty(value = "po_box", required = true)
    private String poBox;

    @JsonProperty(value = "jurisdiction", required = true)
    private String jurisdiction;

    @JsonProperty(value = "service", required = true)
    private String service;

    @JsonProperty(value = "payments", required = true)
    private List<PaymentInfoDTO> payments;

    public CreatePaymentDTO(Payment payment) {
        this.envelopeId = payment.getEnvelopeId();
        this.ccdReference = payment.getCcdReference();
        this.isExceptionRecord = payment.isExceptionRecord();
        this.poBox = payment.getPoBox();
        this.jurisdiction = payment.getJurisdiction();
        this.service = payment.getService();
        this.payments = payment.getPayments().stream().map(PaymentInfoDTO::new).toList();
    }
}
