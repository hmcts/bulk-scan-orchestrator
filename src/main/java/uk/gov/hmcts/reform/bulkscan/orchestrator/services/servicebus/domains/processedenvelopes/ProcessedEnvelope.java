package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.processedenvelopes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessedEnvelope {

    @JsonProperty
    public final String id;

    @JsonProperty("processed_ccd_reference")
    public final Long processedCcdReference;

    @JsonProperty("processed_ccd_type")
    public final EnvelopeCcdAction processedCcdType;

    public ProcessedEnvelope(
        String id,
        Long processedCcdReference,
        EnvelopeCcdAction processedCcdType
    ) {
        this.id = id;
        this.processedCcdReference = processedCcdReference;
        this.processedCcdType = processedCcdType;
    }
}
