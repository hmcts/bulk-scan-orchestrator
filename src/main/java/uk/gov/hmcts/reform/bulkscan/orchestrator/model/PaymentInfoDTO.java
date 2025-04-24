package uk.gov.hmcts.reform.bulkscan.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentInfoDTO {

    @JsonProperty(value = "document_control_number", required = true)
    private String documentControlNumber;
}
