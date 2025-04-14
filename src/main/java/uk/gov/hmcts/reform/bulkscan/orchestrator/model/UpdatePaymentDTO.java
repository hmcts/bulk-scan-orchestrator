package uk.gov.hmcts.reform.bulkscan.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdatePaymentDTO {
    private String envelopeId;

    private String jurisdiction;

    private String exceptionRecordRef;

    private String newCaseRef;

    public UpdatePaymentDTO(UpdatePayment payment) {
        this.envelopeId = payment.getEnvelopeId();
        this.jurisdiction = payment.getJurisdiction();
        this.exceptionRecordRef = payment.getExceptionRecordRef();
        this.newCaseRef = payment.getNewCaseRef();
    }
}
