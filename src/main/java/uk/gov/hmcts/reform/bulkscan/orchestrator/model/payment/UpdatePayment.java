package uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdatePayment {


    @JsonProperty("exception_record_ref")
    public final String exceptionRecordRef;

    @JsonProperty("new_case_ref")
    public final String newCaseRef;

    @JsonProperty("envelope_id")
    public final String envelopeId;

    @JsonProperty("jurisdiction")
    public final String jurisdiction;

    @JsonProperty("status")
    public final String status;


    public UpdatePayment(final uk.gov.hmcts.reform.bulkscan.orchestrator.entity.UpdatePayment  updatePayment) {
        this.exceptionRecordRef = updatePayment.getExceptionRecordRef();
        this.newCaseRef = updatePayment.getNewCaseRef();
        this.envelopeId = updatePayment.getEnvelopeId();
        this.jurisdiction = updatePayment.getJurisdiction();
        this.status = updatePayment.getStatus();
    }


}
