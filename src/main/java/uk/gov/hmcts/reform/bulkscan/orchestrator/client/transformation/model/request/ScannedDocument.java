package uk.gov.hmcts.reform.bulkscan.orchestrator.client.transformation.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class ScannedDocument {

    @JsonProperty("type")
    public final DocumentType type;

    @JsonProperty("subtype")
    public final String subtype;

    @JsonProperty("url")
    public final String url;

    @JsonProperty("control_number")
    public final String controlNumber;

    @JsonProperty("file_name")
    public final String fileName;

    @JsonProperty("scanned_date")
    public final LocalDateTime scannedDate;

    @JsonProperty("delivery_date")
    public final LocalDateTime deliveryDate;

    public ScannedDocument(
        DocumentType type,
        String subtype,
        String url,
        String controlNumber,
        String fileName,
        LocalDateTime scannedDate,
        LocalDateTime deliveryDate
    ) {
        this.type = type;
        this.subtype = subtype;
        this.url = url;
        this.controlNumber = controlNumber;
        this.fileName = fileName;
        this.scannedDate = scannedDate;
        this.deliveryDate = deliveryDate;
    }
}
