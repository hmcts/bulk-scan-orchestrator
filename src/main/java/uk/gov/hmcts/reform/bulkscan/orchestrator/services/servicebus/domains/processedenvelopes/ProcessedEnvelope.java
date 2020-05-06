package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessedEnvelope {

    @JsonProperty
    public final String id;

    @JsonProperty("envelope_id")
    public final String envelopeId;

    @JsonProperty("ccd_id")
    public final Long ccdId;

    @JsonProperty("envelope_ccd_action")
    public final EnvelopeCcdAction envelopeCcdAction;

    public ProcessedEnvelope(
        String envelopeId,
        Long ccdId,
        EnvelopeCcdAction envelopeCcdAction
    ) {
        //Todo delete after processor changes
        this.id = envelopeId;
        this.envelopeId = envelopeId;
        this.ccdId = ccdId;
        this.envelopeCcdAction = envelopeCcdAction;
    }
}
