package uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class UpdatePayment {

    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public final Instant createdAt;

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
        this.createdAt = updatePayment.getCreatedAt();
        this.exceptionRecordRef = updatePayment.getExceptionRecordRef();
        this.newCaseRef = updatePayment.getNewCaseRef();
        this.envelopeId = updatePayment.getEnvelopeId();
        this.jurisdiction = updatePayment.getJurisdiction();
        this.status = updatePayment.getStatus();
    }


}
