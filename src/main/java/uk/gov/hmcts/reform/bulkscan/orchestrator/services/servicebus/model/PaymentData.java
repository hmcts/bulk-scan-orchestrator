package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PaymentData {

    @JsonProperty(value = "document_control_number")
    public final String documentControlNumber;

    public PaymentData(
        String documentControlNumber
    ) {
        this.documentControlNumber = documentControlNumber;
    }
}
