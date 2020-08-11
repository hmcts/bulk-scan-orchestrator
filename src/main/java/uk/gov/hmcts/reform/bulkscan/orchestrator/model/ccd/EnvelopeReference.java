package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents BulkScanEnvelope complex type in CCD case definitions.
 */
public class EnvelopeReference {

    @JsonProperty
    public String id;

    @JsonProperty
    public CaseAction action;

    public EnvelopeReference(
        @JsonProperty(value = "id", required = true) String id,
        @JsonProperty(value = "action", required = true) CaseAction action
    ) {
        this.id = id;
        this.action = action;
    }
}
