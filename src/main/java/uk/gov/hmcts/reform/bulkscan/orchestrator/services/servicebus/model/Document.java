package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {

    public final String fileName;
    public final String controlNumber;
    public final String type;
    public final Instant scannedAt;
    public final String url;
    public final String ocrData;

    // region constructor
    public Document(
        @JsonProperty(value = "file_name", required = true) String fileName,
        @JsonProperty(value = "control_number", required = true) String controlNumber,
        @JsonProperty(value = "type", required = true) String type,
        @JsonProperty(value = "scanned_at", required = true) Instant scannedAt,
        @JsonProperty(value = "url", required = true) String url,
        @JsonProperty(value = "ocr_data") String ocrData
    ) {
        this.fileName = fileName;
        this.controlNumber = controlNumber;
        this.type = type;
        this.scannedAt = scannedAt;
        this.url = url;
        this.ocrData = ocrData;
    }
    // endregion
}
