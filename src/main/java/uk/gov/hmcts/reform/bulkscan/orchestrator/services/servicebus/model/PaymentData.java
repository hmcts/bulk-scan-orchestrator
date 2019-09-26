package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PaymentData {

    public final String documentControlNumber;

    @JsonCreator
    public PaymentData(
        @JsonProperty(value = "document_control_number") String documentControlNumber
    ) {
        this.documentControlNumber = documentControlNumber;
    }
}
