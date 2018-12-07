package uk.gov.hmcts.reform.bulkscan.orchestrator.model.ccd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScannedDocument {

    public final String fileName;
    public final String controlNumber;
    public final String type;
    public final String exceptionReference;
    public final LocalDateTime scannedDate;
    public final CcdDocument url;

    public ScannedDocument(
        @JsonProperty("fileName") String fileName,
        @JsonProperty("controlNumber") String controlNumber,
        @JsonProperty("type") String type,
        @JsonProperty("scannedDate") LocalDateTime scannedDate,
        @JsonProperty("url") CcdDocument url,
        @JsonProperty("exceptionRecordReference") String exceptionReference
    ) {
        this.fileName = fileName;
        this.controlNumber = controlNumber;
        this.type = type;
        this.scannedDate = scannedDate;
        this.url = url;
        this.exceptionReference = exceptionReference;
    }
}
