package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessedEnvelope {

    @JsonProperty("envelope_id")
    public final String envelopeId;

    @JsonProperty("ccd_id")
    public final String ccdId;

    @JsonProperty("envelope_ccd_action")
    public final EnvelopeCcdAction envelopeCcdAction;

    public ProcessedEnvelope(
        String envelopeId,
        Long ccdId,
        EnvelopeCcdAction envelopeCcdAction
    ) {
        this.envelopeId = envelopeId;
        this.ccdId = String.valueOf(ccdId);
        this.envelopeCcdAction = envelopeCcdAction;
    }
}
