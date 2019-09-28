package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PaymentsData {

    @JsonProperty(value = "ccd_reference")
    public final String ccdReference;

    @JsonProperty(value = "jurisdiction")
    public final String jurisdiction;

    @JsonProperty(value = "po_box")
    public final String poBox;

    @JsonProperty(value = "is_exception_record")
    public final boolean isExceptionRecord;

    @JsonProperty(value = "payments")
    public final List<PaymentData> payments;

    public PaymentsData(
        String ccdReference,
        String jurisdiction,
        String poBox,
        boolean isExceptionRecord,
        List<PaymentData> payments
    ) {
        this.ccdReference = ccdReference;
        this.jurisdiction = jurisdiction;
        this.poBox = poBox;
        this.isExceptionRecord = isExceptionRecord;
        this.payments = payments;
    }
}
