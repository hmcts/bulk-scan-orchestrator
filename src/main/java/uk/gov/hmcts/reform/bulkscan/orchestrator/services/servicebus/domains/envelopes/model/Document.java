package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.domains.envelopes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {

    public final String fileName;
    public final String controlNumber;
    public final String type;
    public final String subtype;
    public final Instant scannedAt;
    public final String uuid;
    public final Instant deliveryDate;

    // region constructor
    public Document(
        @JsonProperty(value = "file_name", required = true) String fileName,
        @JsonProperty(value = "control_number", required = true) String controlNumber,
        @JsonProperty(value = "type", required = true) String type,
        @JsonProperty(value = "subtype") String subtype,
        @JsonProperty(value = "scanned_at", required = true) Instant scannedAt,
        @JsonProperty(value = "uuid", required = true) String uuid,
        @JsonProperty(value = "delivery_date") Instant deliveryDate
    ) {
        this.fileName = fileName;
        this.controlNumber = controlNumber;
        this.type = type;
        this.subtype = subtype;
        this.scannedAt = scannedAt;
        this.uuid = uuid;
        this.deliveryDate = deliveryDate;
    }
    // endregion
}
