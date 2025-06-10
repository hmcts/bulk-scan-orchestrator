package uk.gov.hmcts.reform.bulkscan.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdatePaymentDTO {

    @JsonProperty(value = "envelope_id", required = true)
    private String envelopeId;

    @JsonProperty(value = "jurisdiction", required = true)
    private String jurisdiction;

    @JsonProperty(value = "exception_record_ref", required = true)
    private String exceptionRecordRef;

    @JsonProperty(value = "new_case_ref", required = true)
    private String newCaseRef;

    public UpdatePaymentDTO(UpdatePayment payment) {
        this.envelopeId = payment.getEnvelopeId();
        this.jurisdiction = payment.getJurisdiction();
        this.exceptionRecordRef = payment.getExceptionRecordRef();
        this.newCaseRef = payment.getNewCaseRef();
    }
}
