package uk.gov.hmcts.reform.bulkscan.orchestrator.model.payment;

import com.fasterxml.jackson.annotation.JsonProperty;


public class PaymentData {

    @JsonProperty("document_control_number")
    public final String documentControlNumber;

    public PaymentData(String documentControlNumber) {
        this.documentControlNumber = documentControlNumber;
    }

    public PaymentData(final uk.gov.hmcts.reform.bulkscan.orchestrator.entity.PaymentData paymentData) {
        this.documentControlNumber = paymentData.getDocumentControlNumber();
    }
}
