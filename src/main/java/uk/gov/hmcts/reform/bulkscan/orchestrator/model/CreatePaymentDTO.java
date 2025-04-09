package uk.gov.hmcts.reform.bulkscan.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CreatePaymentDTO {

    private String envelopeId;

    private String ccdReference;

    private boolean isExceptionRecord;

    private String poBox;

    private String jurisdiction;

    private String service;

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
