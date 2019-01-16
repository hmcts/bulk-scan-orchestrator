package uk.gov.hmcts.reform.bulkscan.orchestrator.services.servicebus.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {

    public final String fileName;
    public final String controlNumber;
    public final String type;
    public final String subtype;
    public final Instant scannedAt;
    public final String url;

    // region constructor
    public Document(
        @JsonProperty(value = "file_name", required = true) String fileName,
        @JsonProperty(value = "control_number", required = true) String controlNumber,
        @JsonProperty(value = "type", required = true) String type,
        @JsonProperty(value = "subtype") String subtype,
        @JsonProperty(value = "scanned_at", required = true) Instant scannedAt,
        @JsonProperty(value = "url", required = true) String url
    ) {
        this.fileName = fileName;
        this.controlNumber = controlNumber;
        this.type = type;
        this.subtype = subtype;
        this.scannedAt = scannedAt;
        this.url = url;
    }
    // endregion

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Document document = (Document) o;
        return Objects.equals(fileName, document.fileName)
            && Objects.equals(controlNumber, document.controlNumber)
            && Objects.equals(type, document.type)
            && Objects.equals(subtype, document.subtype)
            && Objects.equals(scannedAt, document.scannedAt)
            && Objects.equals(url, document.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, controlNumber, type, subtype, scannedAt, url);
    }
}
