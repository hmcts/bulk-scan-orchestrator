package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OcrDataField {

    @JsonProperty(value = "metadata_field_name")
    public String name;
    @JsonProperty(value = "metadata_field_value")
    public String value;

    @JsonCreator
    public OcrDataField(
        @JsonProperty(value = "metadata_field_name") String name,
        @JsonProperty(value = "metadata_field_value") String value
    ) {
        this.name = name;
        this.value = value;
    }
}
