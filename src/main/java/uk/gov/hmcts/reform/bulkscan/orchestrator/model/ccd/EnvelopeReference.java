package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents BulkScanEnvelope complex type in CCD case definitions.
 */
public class EnvelopeReference {

    @JsonProperty
    public String id;

    @JsonProperty
    public String action;

    public EnvelopeReference(
        @JsonProperty("id") String id,
        @JsonProperty("action") String action
    ) {
        this.id = id;
        this.action = action;
    }
}
