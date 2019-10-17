package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.payments.Labels;

public class UpdatePaymentsCommand implements PaymentCommand {

    @JsonProperty("exception_record_ref")
    public final String exceptionRecordRef;

    @JsonProperty("new_case_ref")
    public final String newCaseRef;

    @JsonProperty("jurisdiction")
    public final String jurisdiction;

    public UpdatePaymentsCommand(
        String exceptionRecordRef,
        String newCaseRef,
        String jurisdiction
    ) {
        this.exceptionRecordRef = exceptionRecordRef;
        this.newCaseRef = newCaseRef;
        this.jurisdiction = jurisdiction;
    }

    @Override
    public String getLabel() {
        return Labels.UPDATE;
    }
}
