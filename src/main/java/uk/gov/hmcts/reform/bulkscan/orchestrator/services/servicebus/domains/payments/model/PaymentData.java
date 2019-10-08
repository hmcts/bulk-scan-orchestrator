package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PaymentData {

    @JsonProperty("document_control_number")
    public final String documentControlNumber;

    public PaymentData(
        String documentControlNumber
    ) {
        this.documentControlNumber = documentControlNumber;
    }
}
