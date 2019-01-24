package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessedEnvelope {

    @JsonProperty
    public final String id;

    public ProcessedEnvelope(String id) {
        this.id = id;
    }
}
