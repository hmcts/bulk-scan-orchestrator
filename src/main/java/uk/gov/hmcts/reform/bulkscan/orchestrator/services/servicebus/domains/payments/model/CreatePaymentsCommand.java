package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CreatePaymentsCommand {

    @JsonProperty("envelope_id")
    public final String envelopeId;

    @JsonProperty("ccd_reference")
    public final String ccdReference;

    @JsonProperty("jurisdiction")
    public final String jurisdiction;

    @JsonProperty("service")
    public final String service;

    @JsonProperty("po_box")
    public final String poBox;

    @JsonProperty("is_exception_record")
    public final boolean isExceptionRecord;

    @JsonProperty("payments")
    public final List<PaymentData> payments;

    public CreatePaymentsCommand(
        String envelopeId,
        String ccdReference,
        String jurisdiction,
        String service,
        String poBox,
        boolean isExceptionRecord,
        List<PaymentData> payments
    ) {
        this.envelopeId = envelopeId;
        this.ccdReference = ccdReference;
        this.jurisdiction = jurisdiction;
        this.service = service;
        this.poBox = poBox;
        this.isExceptionRecord = isExceptionRecord;
        this.payments = payments;
    }
}
