package uk.gov.hmcts.reform.bulkscan.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentInfoDTO {
    private String documentControlNumber;
}
