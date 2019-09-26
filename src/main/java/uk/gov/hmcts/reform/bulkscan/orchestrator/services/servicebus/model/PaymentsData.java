package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PaymentsData {

    public final String ccdReference;
    public final String jurisdiction;
    public final String poBox;
    public final boolean isExceptionRecord;
    public final List<PaymentData> payments;

    @JsonCreator
    public PaymentsData(
        @JsonProperty(value = "ccd_reference") String ccdReference,
        @JsonProperty(value = "jurisdiction") String jurisdiction,
        @JsonProperty(value = "po_box") String poBox,
        @JsonProperty(value = "is_exception_record") boolean isExceptionRecord,
        @JsonProperty(value = "payments") List<PaymentData> payments
    ) {
        this.ccdReference = ccdReference;
        this.jurisdiction = jurisdiction;
        this.poBox = poBox;
        this.isExceptionRecord = isExceptionRecord;
        this.payments = payments;
    }
}
