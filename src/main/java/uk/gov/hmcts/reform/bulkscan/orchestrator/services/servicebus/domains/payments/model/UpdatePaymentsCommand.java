package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpdatePaymentsCommand {

    @JsonProperty("exception_record_ref")
    public final String exceptionRecordRef;

    @JsonProperty("new_case_ref")
    public final String newCaseRef;

    @JsonProperty("envelope_id")
    public final String envelopeId;

    @JsonProperty("service")
    public final String service;

    @JsonProperty("jurisdiction")
    public final String jurisdiction;

    public UpdatePaymentsCommand(
        String exceptionRecordRef,
        String newCaseRef,
        String envelopeId,
        String service,
        String jurisdiction
    ) {
        this.exceptionRecordRef = exceptionRecordRef;
        this.newCaseRef = newCaseRef;
        this.envelopeId = envelopeId;
        this.service = service;
        this.jurisdiction = jurisdiction;
    }
}
